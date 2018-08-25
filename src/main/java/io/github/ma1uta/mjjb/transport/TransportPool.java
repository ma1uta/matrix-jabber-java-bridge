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
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.model.room.CreateRoomRequest;
import io.github.ma1uta.mjjb.dao.RoomAliasDao;
import io.github.ma1uta.mjjb.model.RoomAlias;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.session.XmppSessionConfiguration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.client.Client;

/**
 * Pool of the findAll transports.
 */
public class TransportPool implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransportPool.class);

    private Map<String, Transport> transports = new HashMap<>();

    private final XmppSessionConfiguration xmppSessionConfiguration;

    private final TransportConfiguration transportConfiguration;

    private final Client client;

    private final Jdbi jdbi;

    public TransportPool(XmppSessionConfiguration xmppSessionConfiguration, TransportConfiguration transportConfiguration, Client client,
                         Jdbi jdbi) {
        this.xmppSessionConfiguration = xmppSessionConfiguration;
        this.transportConfiguration = transportConfiguration;
        this.client = client;
        this.jdbi = jdbi;
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

    public Jdbi getJdbi() {
        return jdbi;
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
        } else {
            LOGGER.error("Not found mapped room with id: {}", event.getRoomId());
        }
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
        synchronized (getJdbi()) {
            roomAlias = getJdbi().inTransaction(handle -> {
                RoomAliasDao dao = handle.attach(RoomAliasDao.class);
                RoomAlias founded = dao.findByAlias(alias);
                if (founded == null || !founded.getRoomId().equals(roomId)) {
                    return dao.save(alias, roomId);
                } else {
                    return founded;
                }
            });
        }
        if (roomAlias != null) {
            runTransport(roomAlias);
        }
    }

    /**
     * Start transport.
     *
     * @param roomAlias room alias of the new integrated room.
     * @throws XmppException when cannot connect to the xmpp conference.
     */
    public void runTransport(RoomAlias roomAlias) throws XmppException {
        Transport transport = new Transport(getTransportConfiguration(), getXmppSessionConfiguration(), getClient(), roomAlias,
            getTransportConfiguration().getMasterUserId(), getJdbi());
        transport.init();
        getTransports().put(transport.getRoomAlias().getRoomId(), transport);
    }

    @Override
    public void start() {
        getJdbi().useTransaction(handle -> handle.attach(RoomAliasDao.class).findAll().forEach(roomAlias -> {
            try {
                runTransport(roomAlias);
            } catch (XmppException e) {
                LOGGER.error("Cannot connect to the conference", e);
            } catch (MatrixException e) {
                LOGGER.error("Cannot connect to the homeserver", e);
            }
        }));
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
