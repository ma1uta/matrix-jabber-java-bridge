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

import static io.github.ma1uta.mjjb.dao.RoomAliasDao.ROOM_PATTERN;

import io.dropwizard.lifecycle.Managed;
import io.github.ma1uta.jeon.exception.MatrixException;
import io.github.ma1uta.matrix.Event;
import io.github.ma1uta.matrix.EventContent;
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.model.account.RegisterRequest;
import io.github.ma1uta.matrix.client.model.room.CreateRoomRequest;
import io.github.ma1uta.matrix.client.model.room.RoomId;
import io.github.ma1uta.matrix.events.RoomAliases;
import io.github.ma1uta.matrix.events.RoomMember;
import io.github.ma1uta.matrix.events.RoomMessage;
import io.github.ma1uta.matrix.events.messages.Text;
import io.github.ma1uta.mjjb.dao.AppServerUserDao;
import io.github.ma1uta.mjjb.dao.InviterDao;
import io.github.ma1uta.mjjb.dao.RoomAliasDao;
import io.github.ma1uta.mjjb.model.RoomAlias;
import org.apache.commons.lang3.StringUtils;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.session.XmppSessionConfiguration;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.ws.rs.client.Client;

/**
 * Pool of the findAll transports.
 */
public class TransportPool implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransportPool.class);

    private static final Pattern CONFERENCE = Pattern.compile("(.*)@(.*)");

    private Map<String, Transport> transports = new HashMap<>();

    private final XmppSessionConfiguration xmppSessionConfiguration;

    private final TransportConfiguration transportConfiguration;

    private final Client client;

    private final Jdbi jdbi;

    private final MatrixClient matrixClient;

    private final Map<String, String> inviters = new HashMap<>();

    public TransportPool(XmppSessionConfiguration xmppSessionConfiguration, TransportConfiguration transportConfiguration, Client client,
                         Jdbi jdbi) {
        this.xmppSessionConfiguration = xmppSessionConfiguration;
        this.transportConfiguration = transportConfiguration;
        this.client = client;
        this.jdbi = jdbi;
        this.matrixClient = new MatrixClient(transportConfiguration.getMatrixHomeserver(), client, true, false);
        this.matrixClient.setUserId(transportConfiguration.getMasterUserId());
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

    public MatrixClient getMatrixClient() {
        return matrixClient;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public Map<String, String> getInviters() {
        return inviters;
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
        getJdbi().useTransaction(handle -> {
            handle.attach(RoomAliasDao.class).findAll().forEach(roomAlias -> {
                try {
                    runTransport(roomAlias);
                } catch (XmppException e) {
                    LOGGER.error("Cannot connect to the conference", e);
                } catch (MatrixException e) {
                    LOGGER.error("Cannot connect to the homeserver", e);
                }
            });
            checkMasterBot(handle);
            loadInviters(handle);
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

    /**
     * Push event to the bridge.
     *
     * @param event event.
     */
    public void event(Event event) {
        EventContent content = event.getContent();
        boolean commandInvoked = false;
        if (content instanceof RoomMember) {
            commandInvoked = joinOrLeave(event);
        } else if (content instanceof RoomMessage) {
            commandInvoked = processEvent(event);
        } else if (content instanceof RoomAliases) {
            commandInvoked = createOrRemoveTransport(event);
        }

        if (!commandInvoked) {
            Transport transport = getTransports().get(event.getRoomId());
            if (transport != null) {
                transport.event(event);
            } else {
                LOGGER.error("Not found mapped room with id: {}", event.getRoomId());
            }
        }
    }

    protected boolean processEvent(Event event) {
        EventContent content = event.getContent();
        if (content instanceof Text) {
            String body = ((Text) content).getBody();
            if (StringUtils.isNotBlank(body) && body.trim().startsWith(getTransportConfiguration().getMasterUserId())) {
                String[] arguments = body.trim().split("\\s");
                if (arguments.length < 2) {
                    getMatrixClient().event().sendNotice(event.getRoomId(), "Missing command.");
                    return false;
                }

                String command = Arrays.stream(arguments).skip(1).collect(Collectors.joining(" "));

                switch (arguments[1]) {
                    case "connect":
                        return connect(event, command);
                    case "disconnect":
                        return disconnect(event);
                    case "info":
                        return info(event);
                    default:
                        return false;
                }
            }
        }
        return false;
    }

    protected boolean connect(Event event, String command) {
        Matcher matcher = CONFERENCE.matcher(command);
        if (!matcher.matches()) {
            getMatrixClient().event().sendNotice(event.getRoomId(), "Failed to parse conference url.");
            return false;
        }

        String alias = String.format("#%s_%s_%s:%s", getTransportConfiguration().getPrefix(), matcher.group(1), matcher.group(2),
            Id.domain(getTransportConfiguration().getMasterUserId()));
        RoomId roomId = new RoomId();
        roomId.setRoomId(event.getRoomId());
        getMatrixClient().room().newAlias(roomId, alias);
        return true;
    }

    protected boolean disconnect(Event event) {
        String inviter = getInviters().get(event.getRoomId());
        if (inviter == null || !inviter.equals(event.getSender())) {
            return false;
        }

        Transport transport = getTransports().remove(event.getRoomId());
        if (transport != null) {
            transport.remove();
            removeInviter(event.getRoomId());
        }

        return false;
    }

    protected boolean info(Event event) {
        Transport transport = getTransports().get(event.getRoomId());

        if (transport != null) {
            StringBuilder sb = new StringBuilder();
            RoomAlias roomAlias = transport.getRoomAlias();

            sb.append("Matrix room id: ").append(roomAlias.getRoomId()).append("<br>");
            sb.append("Matrix room alias: ").append(roomAlias.getAlias()).append("<br>");
            sb.append("Xmpp conference: ").append(roomAlias.getConferenceJid()).append("<br>");
            sb.append("Pupped users:<br>Matrix users:<br>");
            transport.getMxToXmppUsers().forEach((userId, nick) -> sb.append(userId).append(" -> ").append(nick).append("<br>"));
            sb.append("Xmpp users:<br>");
            transport.getXmppToMxUsers().forEach((nick, userId) -> sb.append(nick).append(" -> ").append(userId).append("<br>"));

            String formatted = sb.toString();
            getMatrixClient().event().sendFormattedNotice(event.getRoomId(), Jsoup.parse(formatted).text(), formatted);
            return true;
        }

        return false;
    }

    protected boolean createOrRemoveTransport(Event event) {
        Optional<String> foundAlias = ((RoomAliases) event.getContent()).getAliases().stream()
            .filter(alias -> ROOM_PATTERN.matcher(Id.localpart(alias)).matches())
            .findAny();
        if (foundAlias.isPresent()) {
            String alias = foundAlias.get();
            try {
                runTransport(event.getRoomId(), alias);
                return true;
            } catch (XmppException e) {
                LOGGER.error(String.format("Cannot create transport in the room %s with conference %s", event.getRoomId(), alias), e);
            }
        } else {
            Transport transportToRemove = getTransports().remove(event.getRoomId());
            if (transportToRemove != null) {
                transportToRemove.remove();
                return true;
            }
        }
        return false;
    }

    protected boolean joinOrLeave(Event event) {
        RoomMember content = (RoomMember) event.getContent();

        if (!getTransportConfiguration().getMasterUserId().equals(event.getStateKey())) {
            return false;
        }

        switch (content.getMembership()) {
            case Event.MembershipState.INVITE:
                addInviter(event.getRoomId(), event.getSender());
                getMatrixClient().room().joinByIdOrAlias(event.getRoomId());
                return true;
            case Event.MembershipState.BAN:
            case Event.MembershipState.LEAVE:
                removeInviter(event.getRoomId());
                getTransports().remove(event.getRoomId()).remove();
                return true;
            default:
                return false;
        }
    }

    /**
     * Create new room with specified alias.
     *
     * @param roomAlias room alias.
     */
    public void createTransport(String roomAlias) {
        CreateRoomRequest request = new CreateRoomRequest();
        request.setVisibility(Event.Visibility.SHARED);
        request.setRoomAliasName(roomAlias);
        request.setGuestCanJoin(true);
        request.setDirect(false);

        MatrixClient client = getMatrixClient();

        client.room().create(request);
        client.room().joinByIdOrAlias(roomAlias);
    }

    protected void checkMasterBot(Handle handle) {
        String masterUserId = getTransportConfiguration().getMasterUserId();
        AppServerUserDao dao = handle.attach(AppServerUserDao.class);
        if (dao.count(masterUserId) == 0) {
            String nick = Id.localpart(masterUserId);
            try {
                RegisterRequest request = new RegisterRequest();
                request.setUsername(nick);
                request.setInitialDeviceDisplayName(nick);
                getMatrixClient().account().register(request);
            } catch (MatrixException e) {
                LOGGER.warn("master user already registered", e);
            }
            dao.save(masterUserId);
        }
    }

    protected void loadInviters(Handle handle) {
        getInviters().putAll(handle.attach(InviterDao.class).load(handle));
    }

    protected void addInviter(String roomId, String userId) {
        getJdbi().useTransaction(handle -> {
            handle.attach(InviterDao.class).save(roomId, userId);
            getInviters().put(roomId, userId);
        });
    }

    protected void removeInviter(String roomId) {
        getJdbi().useTransaction(handle -> {
            handle.attach(InviterDao.class).remove(roomId);
            getInviters().remove(roomId);
        });
    }
}
