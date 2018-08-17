/*
 * Copyright sablintolya@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.ma1uta.mjjb.transport;

import io.dropwizard.lifecycle.Managed;
import io.github.ma1uta.jeon.exception.MatrixException;
import io.github.ma1uta.matrix.Event;
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.model.room.CreateRoomRequest;
import io.github.ma1uta.matrix.events.RoomAliases;
import io.github.ma1uta.mjjb.dao.RoomAliasDao;
import io.github.ma1uta.mjjb.model.RoomAlias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.session.XmppSessionConfiguration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

/**
 * Pool of the findAll transports.
 */
public class TransportPool implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransportPool.class);

    private Map<String, Transport> transports = new HashMap<>();

    private final XmppSessionConfiguration xmppSessionConfiguration;

    private final TransportConfiguration transportConfiguration;

    private final Client client;

    private final PersistenceService service;

    public TransportPool(XmppSessionConfiguration xmppSessionConfiguration, TransportConfiguration transportConfiguration, Client client,
                         PersistenceService service) {
        this.xmppSessionConfiguration = xmppSessionConfiguration;
        this.transportConfiguration = transportConfiguration;
        this.client = client;
        this.service = service;
    }

    public Map<String, Transport> getTransports() {
        return transports;
    }

    public XmppSessionConfiguration getXmppSessionConfiguration() {
        return xmppSessionConfiguration;
    }

    public TransportConfiguration getTransportConfiguration() {
        return transportConfiguration;
    }

    public Client getClient() {
        return client;
    }

    public PersistenceService getService() {
        return service;
    }

    /**
     * Map the matrix's event to the xmpp message and send it.
     *
     * @param event event to map.
     */
    public void event(Event event) {
        Transport transport = getTransports().get(event.getRoomId());
        if (transport != null) {
            transport.event(event);
        } else if (event.getContent() instanceof RoomAliases) {
            ((RoomAliases) event.getContent()).getAliases().stream()
                .filter(alias -> RoomAliasDao.ROOM_PATTERN.matcher(Id.localpart(alias)).matches()).findFirst()
                .ifPresent(s -> {
                    try {
                        runTransport(event.getRoomId(), s);
                    } catch (XmppException e) {
                        throw new MatrixException(MatrixException.M_INTERNAL, e.getMessage(),
                            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
                    }
                });
        } else {
            LOGGER.error("Not found mapped room with id: {}", event.getRoomId());
        }
    }

    /**
     * Create new room with specified alias.
     *
     * @param roomAlias room alias.
     */
    public void createTransport(String roomAlias) {
        MatrixClient mxClient = new MatrixClient(getTransportConfiguration().getMatrixHomeserver(), getClient(), true, false);
        mxClient.setAccessToken(getTransportConfiguration().getAccessToken());
        mxClient.setUserId(getTransportConfiguration().getMasterUserId());

        CreateRoomRequest request = new CreateRoomRequest();
        request.setVisibility(Event.Visibility.SHARED);
        request.setRoomAliasName(roomAlias);
        request.setGuestCanJoin(true);
        request.setDirect(false);
        mxClient.room().create(request);
    }

    /**
     * Create and start transport.
     *
     * @param roomId room id.
     * @param alias  room alias.
     * @throws XmppException when cannot connect to the xmpp conference.
     */
    public void runTransport(String roomId, String alias) throws XmppException {
        RoomAlias roomAlias;
        RoomAliasDao aliasDao = getService().getAliasDao();
        synchronized (aliasDao) {
            roomAlias = aliasDao.findByAlias(alias);
            if (roomAlias == null) {
                roomAlias = aliasDao.persist(alias, roomId);
            } else if (!roomAlias.getRoomId().equals(roomId)) {
                roomAlias = aliasDao.persist(roomAlias);
            }
        }
        runTransport(roomAlias);
    }

    /**
     * Start transport.
     *
     * @param roomAlias room alias of the new integrated room.
     * @throws XmppException when cannot connect to the xmpp conference.
     */
    public void runTransport(RoomAlias roomAlias) throws XmppException {
        Transport transport = new Transport(getTransportConfiguration(), getXmppSessionConfiguration(), getClient(), roomAlias,
            getTransportConfiguration().getMasterUserId(), getService());
        transport.init();
        getTransports().put(transport.getRoomAlias().getRoomId(), transport);
    }

    @Override
    public void start() {
        getService().getAliasDao().findAll().forEach(roomList -> {
            try {
                runTransport(roomList);
            } catch (XmppException e) {
                LOGGER.error("Cannot connect to the conference", e);
            }
        });
    }

    @Override
    public void stop() {
        getTransports().forEach((roomId, transport) -> {
            try {
                transport.close();
            } catch (IOException e) {
                LOGGER.error("Cannot close xmpp connection", e);
            }
        });
    }
}
