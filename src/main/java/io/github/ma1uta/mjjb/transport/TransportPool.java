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
import rocks.xmpp.core.stanza.MessageEvent;
import rocks.xmpp.core.stanza.PresenceEvent;
import rocks.xmpp.extensions.component.accept.ExternalComponent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.ws.rs.client.Client;

/**
 * Pool of the findAll mxTransports.
 */
public class TransportPool implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransportPool.class);

    private static final Pattern CONFERENCE = Pattern.compile("(.*)@(.*)");

    private Map<String, Transport> mxTransports = new HashMap<>();

    private Map<String, Transport> xmppTransports = new HashMap<>();

    private final XmppSessionConfiguration xmppSessionConfiguration;

    private final TransportConfiguration transportConfiguration;

    private final Client client;

    private final Jdbi jdbi;

    private MatrixClient matrixClient;

    private ExternalComponent xmppClient;

    private final Map<String, String> inviters = new HashMap<>();

    private volatile boolean maintenanceMode = false;

    public TransportPool(XmppSessionConfiguration xmppSessionConfiguration, TransportConfiguration transportConfiguration, Client client,
                         Jdbi jdbi) {
        this.xmppSessionConfiguration = xmppSessionConfiguration;
        this.transportConfiguration = transportConfiguration;
        this.client = client;
        this.jdbi = jdbi;
    }

    public Map<String, Transport> getMxTransports() {
        return mxTransports;
    }

    public Map<String, Transport> getXmppTransports() {
        return xmppTransports;
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

    public ExternalComponent getXmppClient() {
        return xmppClient;
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
     */
    public void runTransport(String roomId, String alias) {
        RoomAlias roomAlias;
        synchronized (getJdbi()) {
            roomAlias = getJdbi().inTransaction(handle -> {
                RoomAliasDao dao = handle.attach(RoomAliasDao.class);
                RoomAlias founded = dao.findByAlias(alias);
                if (founded == null || !founded.getRoomId().equals(roomId)) {
                    LOGGER.debug("Save a new transport");
                    return dao.save(alias, roomId);
                } else {
                    LOGGER.debug("Transport already saved");
                    return founded;
                }
            });
        }
        if (roomAlias != null) {
            LOGGER.debug("Run transport");
            runTransport(roomAlias);
        }
    }

    /**
     * Start transport.
     *
     * @param roomAlias room alias of the new integrated room.
     */
    public void runTransport(RoomAlias roomAlias) {
        Transport transport = new Transport(getTransportConfiguration(), getXmppClient(), getClient(), roomAlias, getJdbi());
        transport.init();
        LOGGER.info("Init the transport");
        getMxTransports().put(transport.getRoomAlias().getRoomId(), transport);
        getXmppTransports().put(transport.getRoomAlias().getConferenceJid(), transport);
    }

    @Override
    public void start() {
        this.maintenanceMode = true;
        try {
            getJdbi().useTransaction(handle -> {
                createMatrixClient();
                checkMasterBot(handle);
                loadInviters(handle);
                connectToXmpp();
                handle.attach(RoomAliasDao.class).findAll().forEach(roomAlias -> {
                    try {
                        runTransport(roomAlias);
                    } catch (MatrixException e) {
                        LOGGER.error("Cannot connect to the homeserver", e);
                    }
                });
            });
        } finally {
            this.maintenanceMode = false;
        }
    }

    @Override
    public void stop() {
        this.maintenanceMode = true;
        try {
            getMxTransports().values().forEach(Transport::close);
            getXmppClient().close();
        } catch (XmppException e) {
            LOGGER.error("Cannot close the xmpp connection", e);
        } finally {
            this.maintenanceMode = false;
        }
    }

    /**
     * Push event to the bridge.
     *
     * @param event event.
     */
    public void event(Event event) {
        EventContent content = event.getContent();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Event: {}", event.getEventId());
            LOGGER.debug("Type: {}", event.getType());
            LOGGER.debug("Room: {}", event.getRoomId());
            LOGGER.debug("Sender: {}", event.getSender());
        }

        boolean commandInvoked = false;
        if (content instanceof RoomMember) {
            commandInvoked = joinOrLeave(event);
        } else if (content instanceof RoomMessage) {
            commandInvoked = processEvent(event);
        } else if (content instanceof RoomAliases) {
            commandInvoked = createOrRemoveTransport(event);
        }

        if (!commandInvoked) {
            Transport transport = getMxTransports().get(event.getRoomId());
            if (transport != null) {
                transport.event(event);
            } else {
                LOGGER.error("Not found mapped room with id: {}", event.getRoomId());
                tryToRunTransport(event);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void tryToRunTransport(Event event) {
        if (getMatrixClient().room().joinedRooms().contains(event.getRoomId())) {
            LOGGER.info("Matrix bot is a member");
            Map<String, Object> content;
            try {
                content = getMatrixClient().event().eventContent(event.getRoomId(), Event.EventType.ROOM_ALIASES);
            } catch (MatrixException e) {
                LOGGER.error("Cannot get m.room.aliases events", e);
                return;
            }
            Object objAliases = content.get("Aliases");
            if (objAliases instanceof List) {
                List<String> aliases = (List<String>) objAliases;
                Optional<String> foundedAlias = aliases.stream().filter(alias -> ROOM_PATTERN.matcher(alias).matches()).findAny();
                if (foundedAlias.isPresent()) {
                    LOGGER.info("Room has the alias, run transport");
                    String roomAlias = foundedAlias.get();
                    runTransport(event.getRoomId(), roomAlias);
                    Transport transport = getMxTransports().get(roomAlias);
                    if (transport != null) {
                        transport.event(event);
                    } else {
                        LOGGER.error("Cannot run transport");
                    }
                } else {
                    LOGGER.info("Room doesn't have the alias, skip");
                }
            }
        } else {
            LOGGER.info("Matrix bot is not a member, skip");
        }
    }

    protected boolean processEvent(Event event) {
        EventContent content = event.getContent();
        if (content instanceof Text) {
            Text textContent = (Text) content;
            LOGGER.debug("m.room.message: {}", textContent.getMsgtype());
            String body = textContent.getBody();
            if (StringUtils.isNotBlank(body) && body.trim().startsWith(Id.localpart(getTransportConfiguration().getMasterUserId()))) {
                String[] arguments = body.trim().split("\\s");
                if (arguments.length < 2) {
                    getMatrixClient().event().sendNotice(event.getRoomId(), "Missing command.");
                    return false;
                }

                String command = Arrays.stream(arguments).skip(2).collect(Collectors.joining(" "));

                LOGGER.debug("Command: {}", command);

                switch (arguments[1]) {
                    case "connect":
                        return connect(event, command);
                    case "disconnect":
                        return disconnect(event);
                    case "info":
                        return info(event);
                    case "members":
                        return members(event);
                    default:
                        return false;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    protected boolean connect(Event event, String command) {
        Matcher matcher = CONFERENCE.matcher(command);
        if (!matcher.matches()) {
            getMatrixClient().event().sendNotice(event.getRoomId(), "Failed to parse conference url.");
            return false;
        }

        String alias = String.format("#%s%s_%s:%s", getTransportConfiguration().getPrefix(), matcher.group(1), matcher.group(2),
            Id.domain(getTransportConfiguration().getMasterUserId()));
        RoomId roomId = new RoomId();
        roomId.setRoomId(event.getRoomId());
        try {
            Map<String, Object> content = getMatrixClient().event().eventContent(event.getRoomId(), Event.EventType.ROOM_ALIASES);
            Optional<String> foundedAlias = ((List<String>) content.get("aliases")).stream()
                .filter(a -> ROOM_PATTERN.matcher(a).matches()).findAny();
            if (foundedAlias.isPresent()) {
                LOGGER.warn("Alias already exist, skip");
                return true;
            } else {
                LOGGER.debug("Room doesn't have alias, add");
            }
        } catch (MatrixException e) {
            LOGGER.warn("Cannot get m.room.aliases");
        } catch (ClassCastException e) {
            LOGGER.error("Wrong event content", e);
        }
        try {
            getMatrixClient().room().newAlias(roomId, alias);
        } catch (MatrixException e) {
            LOGGER.error("Cannot set a new alias", e);
        }
        return true;
    }

    protected boolean disconnect(Event event) {
        String inviter = getInviters().get(event.getRoomId());
        if (inviter == null || !inviter.equals(event.getSender())) {
            return false;
        }

        Transport transport = getMxTransports().remove(event.getRoomId());
        if (transport != null) {
            transport.remove();
            removeInviter(event.getRoomId());
        } else {
            MatrixClient matrixClient = getMatrixClient();
            if (matrixClient.room().joinedRooms().contains(event.getRoomId())) {
                matrixClient.room().leave(event.getRoomId());
            }
        }

        return false;
    }

    protected boolean info(Event event) {
        Transport transport = getMxTransports().get(event.getRoomId());

        if (transport != null) {
            StringBuilder sb = new StringBuilder();
            RoomAlias roomAlias = transport.getRoomAlias();

            sb.append("Matrix room id: ").append(roomAlias.getRoomId()).append("<br>");
            sb.append("Matrix room alias: ").append(roomAlias.getAlias()).append("<br>");
            sb.append("Xmpp conference: ").append(roomAlias.getConferenceJid()).append("<br>");

            String formatted = sb.toString();
            getMatrixClient().event().sendFormattedNotice(event.getRoomId(), Jsoup.parse(formatted).text(), formatted);
            return true;
        }

        return false;
    }

    protected boolean members(Event event) {
        Transport transport = getMxTransports().get(event.getRoomId());

        if (transport != null) {
            StringBuilder sb = new StringBuilder();

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
        LOGGER.info("Check the alias");
        try {
            Optional<String> foundAlias = ((RoomAliases) event.getContent()).getAliases().stream()
                .filter(alias -> ROOM_PATTERN.matcher(Id.localpart(alias)).matches())
                .findAny();

            if (foundAlias.isPresent()) {
                LOGGER.info("Room has the alias, start transport");
                runTransport(event.getRoomId(), foundAlias.get());
                return true;
            } else {
                LOGGER.info("Room has not the alias, remove transport");
                Optional<String> joinedRoom = getMatrixClient().room().joinedRooms().stream()
                    .filter(roomId -> roomId.equals(event.getRoomId()))
                    .findAny();
                if (!joinedRoom.isPresent()) {
                    Transport transportToRemove = getMxTransports().remove(event.getRoomId());
                    if (transportToRemove != null) {
                        LOGGER.info("Remove transport");
                        transportToRemove.remove();
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Cannot create or remove the transport", e);
        }
        return false;
    }

    protected boolean joinOrLeave(Event event) {
        RoomMember content = (RoomMember) event.getContent();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("m.room.member: {}", content.getMembership());
            LOGGER.debug("state_key", event.getStateKey());
        }

        if (!getTransportConfiguration().getMasterUserId().equals(event.getStateKey()) || this.maintenanceMode) {
            LOGGER.debug("Skip");
            return false;
        }

        switch (content.getMembership()) {
            case Event.MembershipState.JOIN:
                LOGGER.debug("Master bot has joined to the room");
                return true;
            case Event.MembershipState.INVITE:
                LOGGER.info("Invite the master bot");
                addInviter(event.getRoomId(), event.getSender());
                getMatrixClient().room().joinByIdOrAlias(event.getRoomId());
                return true;
            case Event.MembershipState.BAN:
            case Event.MembershipState.LEAVE:
                if (getMatrixClient().room().joinedRooms().contains(event.getRoomId())) {
                    LOGGER.info("Master bot doesn't join, skip");
                    return false;
                }

                LOGGER.info("Remove transport");
                removeInviter(event.getRoomId());
                Transport transport = getMxTransports().remove(event.getRoomId());
                if (transport != null) {
                    if (LOGGER.isInfoEnabled()) {
                        RoomAlias roomAlias = transport.getRoomAlias();
                        LOGGER.info("Room id: {}", roomAlias.getRoomId());
                        LOGGER.info("Alias: {}", roomAlias.getAlias());
                        LOGGER.info("Conference url: {}", roomAlias.getConferenceJid());
                    }
                    transport.remove();
                }
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
        String nick = Id.localpart(masterUserId);
        if (dao.count(nick) == 0) {
            try {
                RegisterRequest request = new RegisterRequest();
                request.setUsername(nick);
                request.setInitialDeviceDisplayName(nick);
                getMatrixClient().account().register(request);
            } catch (MatrixException e) {
                LOGGER.warn("master user already registered", e);
            }
            dao.save(nick);
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

    protected void createMatrixClient() {
        TransportConfiguration config = getTransportConfiguration();
        this.matrixClient = new MatrixClient(config.getMatrixHomeserver(), getClient(), true, false);
        this.matrixClient.setUserId(config.getMasterUserId());
        this.matrixClient.setAccessToken(config.getAccessToken());
    }

    protected void connectToXmpp() {
        TransportConfiguration config = getTransportConfiguration();
        this.xmppClient = ExternalComponent.create(
            config.getXmppComponentName(),
            config.getXmppShareSecret(),
            getXmppSessionConfiguration(),
            config.getXmppHostName(),
            config.getXmppPort()
        );
        this.xmppClient.addInboundMessageListener(this::xmppMessage);
        this.xmppClient.addInboundPresenceListener(this::xmppPresence);

        try {
            this.xmppClient.connect();
        } catch (XmppException e) {
            LOGGER.error("Cannot connect to the server", e);
            throw new RuntimeException(e);
        }
    }


    protected void xmppMessage(MessageEvent messageEvent) {
        Transport transport = getXmppTransports().get(messageEvent.getMessage().getFrom().asBareJid().toEscapedString());
        if (transport != null) {
            transport.xmppMessage(messageEvent);
        }
    }

    protected void xmppPresence(PresenceEvent presenceEvent) {
        Transport transport = getXmppTransports().get(presenceEvent.getPresence().getFrom().asBareJid().toEscapedString());
        if (transport != null) {
            transport.xmppPresence(presenceEvent);
        }
    }
}
