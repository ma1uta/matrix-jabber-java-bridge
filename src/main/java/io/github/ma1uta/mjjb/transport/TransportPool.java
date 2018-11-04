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
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.client.AppServiceClient;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.factory.jaxrs.JaxRsRequestFactory;
import io.github.ma1uta.matrix.client.model.account.RegisterRequest;
import io.github.ma1uta.matrix.client.model.room.CreateRoomRequest;
import io.github.ma1uta.matrix.client.model.room.RoomId;
import io.github.ma1uta.matrix.event.Event;
import io.github.ma1uta.matrix.event.RoomAliases;
import io.github.ma1uta.matrix.event.RoomEvent;
import io.github.ma1uta.matrix.event.RoomMember;
import io.github.ma1uta.matrix.event.RoomMessage;
import io.github.ma1uta.matrix.event.content.RoomAliasesContent;
import io.github.ma1uta.matrix.event.content.RoomMemberContent;
import io.github.ma1uta.matrix.event.content.RoomMessageContent;
import io.github.ma1uta.matrix.event.message.Text;
import io.github.ma1uta.matrix.impl.exception.MatrixException;
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

    private AppServiceClient matrixClient;

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

    public AppServiceClient getMatrixClient() {
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
    public void runTransport(String roomId, String alias, Handle handle) {
        RoomAlias roomAlias;
        RoomAliasDao dao = handle.attach(RoomAliasDao.class);
        RoomAlias founded = dao.findByAlias(alias);
        if (founded == null || !founded.getRoomId().equals(roomId)) {
            LOGGER.debug("Save a new transport.");
            roomAlias = dao.save(alias, roomId);
        } else {
            LOGGER.debug("Transport already saved.");
            roomAlias = founded;
        }
        LOGGER.debug("Run transport.");
        runTransport(roomAlias);
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
    public void event(Event event, Handle handle) {
        LOGGER.debug("Type: {}", event.getType());
        if (event instanceof RoomEvent) {
            event((RoomEvent) event, handle);
        }
    }

    protected void event(RoomEvent event, Handle handle) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Event: {}", event.getEventId());
            LOGGER.debug("Room: {}", event.getRoomId());
            LOGGER.debug("Sender: {}", event.getSender());
        }

        boolean commandInvoked = false;
        if (event instanceof RoomMember) {
            commandInvoked = joinOrLeave((RoomMember) event, handle);
        } else if (event instanceof RoomMessage) {
            commandInvoked = processEvent((RoomMessage) event, handle);
        } else if (event instanceof RoomAliases) {
            commandInvoked = createOrRemoveTransport((RoomAliases) event, handle);
        }

        if (!commandInvoked) {
            Transport transport = getMxTransports().get(event.getRoomId());
            if (transport != null) {
                transport.event(event);
            } else {
                LOGGER.error("Not found mapped room with id: {}", event.getRoomId());
                tryToRunTransport(event, handle);
            }
        }

    }

    @SuppressWarnings("unchecked")
    protected void tryToRunTransport(RoomEvent event, Handle handle) {
        if (!getMatrixClient().room().joinedRooms().join().contains(event.getRoomId())) {
            LOGGER.info("Matrix bot is not a member, skip.");
            return;
        }

        LOGGER.info("Matrix bot is a member.");
        RoomAliasesContent content;
        try {
            content = (RoomAliasesContent) getMatrixClient().event().eventContent(event.getRoomId(), Event.EventType.ROOM_ALIASES).join();
        } catch (MatrixException e) {
            LOGGER.error("Cannot get m.room.aliases events.", e);
            return;
        }
        List<String> aliases = content.getAliases();
        Optional<String> foundedAlias = aliases.stream().filter(alias -> ROOM_PATTERN.matcher(alias).matches()).findAny();
        if (!foundedAlias.isPresent()) {
            LOGGER.info("Room doesn't have the alias, skip.");
            return;
        }

        LOGGER.info("Room has the alias, run transport.");
        String roomAlias = foundedAlias.get();
        runTransport(event.getRoomId(), roomAlias, handle);
        Transport transport = getMxTransports().get(roomAlias);
        if (transport != null) {
            transport.event(event);
        } else {
            LOGGER.error("Cannot run transport.");
        }
    }

    protected boolean processEvent(RoomMessage event, Handle handle) {
        RoomMessageContent content = event.getContent();
        if (content instanceof Text) {
            Text textContent = (Text) content;
            LOGGER.debug("m.room.message: {}", textContent.getMsgtype());
            String body = textContent.getBody();
            if (StringUtils.isNotBlank(body) && body.trim()
                .startsWith(Id.getInstance().localpart(getTransportConfiguration().getMasterUserId()))) {
                String[] arguments = body.trim().split("\\s");
                if (arguments.length < 2) {
                    getMatrixClient().event().sendNotice(event.getRoomId(), "Missing command.").join();
                    return false;
                }

                String command = Arrays.stream(arguments).skip(2).collect(Collectors.joining(" "));

                LOGGER.debug("Command: {}", command);

                switch (arguments[1]) {
                    case "connect":
                        return connect(event, command);
                    case "disconnect":
                        return disconnect(event, handle);
                    case "info":
                        return info(event);
                    case "members":
                        return members(event);
                    default:
                        // nothing
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    protected boolean connect(RoomMessage event, String command) {
        Matcher matcher = CONFERENCE.matcher(command);
        if (!matcher.matches()) {
            getMatrixClient().event().sendNotice(event.getRoomId(), "Failed to parse conference url.").join();
            return false;
        }

        String alias = String.format("#%s%s_%s:%s", getTransportConfiguration().getPrefix(), matcher.group(1), matcher.group(2),
            Id.getInstance().domain(getTransportConfiguration().getMasterUserId()));
        RoomId roomId = new RoomId();
        roomId.setRoomId(event.getRoomId());
        try {
            RoomAliasesContent content = (RoomAliasesContent) getMatrixClient().event()
                .eventContent(event.getRoomId(), Event.EventType.ROOM_ALIASES)
                .join();
            Optional<String> foundedAlias = content.getAliases().stream().filter(a -> ROOM_PATTERN.matcher(a).matches()).findAny();
            if (foundedAlias.isPresent()) {
                LOGGER.warn("Alias already exist, skip.");
                return true;
            } else {
                LOGGER.debug("Room doesn't have alias, add.");
            }
        } catch (MatrixException e) {
            LOGGER.warn("Cannot get m.room.aliases.", e);
        } catch (ClassCastException e) {
            LOGGER.error("Wrong event content.", e);
        }
        try {
            getMatrixClient().room().createAlias(roomId, alias).join();
        } catch (MatrixException e) {
            LOGGER.error("Cannot set new alias.", e);
        }
        return true;
    }

    protected boolean disconnect(RoomMessage event, Handle handle) {
        String inviter = getInviters().get(event.getRoomId());
        if (inviter == null || !inviter.equals(event.getSender())) {
            return false;
        }

        Transport transport = getMxTransports().remove(event.getRoomId());
        if (transport != null) {
            transport.remove(handle);
            removeInviter(event.getRoomId(), handle);
        }
        MatrixClient matrixClient = getMatrixClient();
        if (matrixClient.room().joinedRooms().join().contains(event.getRoomId())) {
            matrixClient.room().leave(event.getRoomId()).join();
        }

        return true;
    }

    protected boolean info(RoomMessage event) {
        Transport transport = getMxTransports().get(event.getRoomId());

        if (transport == null) {
            return false;
        }

        StringBuilder sb = new StringBuilder();
        RoomAlias roomAlias = transport.getRoomAlias();

        sb.append("Matrix room id: ").append(roomAlias.getRoomId()).append("<br>");
        sb.append("Matrix room alias: ").append(roomAlias.getAlias()).append("<br>");
        sb.append("Xmpp conference: ").append(roomAlias.getConferenceJid()).append("<br>");

        String formatted = sb.toString();
        getMatrixClient().event().sendFormattedNotice(event.getRoomId(), Jsoup.parse(formatted).text(), formatted).join();
        return true;
    }

    protected boolean members(RoomMessage event) {
        Transport transport = getMxTransports().get(event.getRoomId());

        if (transport == null) {
            return false;
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Pupped users:<br>Matrix users:<br>");
        transport.getMxToXmppUsers().forEach((userId, nick) -> sb.append(userId).append(" -> ").append(nick).append("<br>"));
        sb.append("Xmpp users:<br>");
        transport.getXmppToMxUsers().forEach((nick, userId) -> sb.append(nick).append(" -> ").append(userId).append("<br>"));

        String formatted = sb.toString();
        getMatrixClient().event().sendFormattedNotice(event.getRoomId(), Jsoup.parse(formatted).text(), formatted).join();
        return true;
    }

    protected boolean createOrRemoveTransport(RoomAliases event, Handle handle) {
        LOGGER.info("Check the alias.");
        try {
            Optional<String> foundAlias = event.getContent().getAliases().stream()
                .filter(alias -> ROOM_PATTERN.matcher(Id.getInstance().localpart(alias)).matches())
                .findAny();

            if (foundAlias.isPresent()) {
                LOGGER.info("Room has the alias, start transport.");
                runTransport(event.getRoomId(), foundAlias.get(), handle);
                return true;
            } else {
                LOGGER.info("Room has not the alias, remove transport.");
                Optional<String> joinedRoom = getMatrixClient().room().joinedRooms().join().stream()
                    .filter(roomId -> roomId.equals(event.getRoomId()))
                    .findAny();
                if (!joinedRoom.isPresent()) {
                    Transport transportToRemove = getMxTransports().remove(event.getRoomId());
                    if (transportToRemove != null) {
                        LOGGER.info("Remove transport.");
                        transportToRemove.remove(handle);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Cannot create or remove the transport", e);
        }
        return false;
    }

    protected boolean joinOrLeave(RoomMember event, Handle handle) {
        RoomMemberContent content = event.getContent();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("m.room.member: {}", content.getMembership());
            LOGGER.debug("state_key: {}", event.getStateKey());
        }

        if (this.maintenanceMode) {
            LOGGER.debug("Maintenance mode.");
            return false;
        }

        if (!getTransportConfiguration().getMasterUserId().equals(event.getStateKey())) {
            LOGGER.debug("State key isn't equal master user id.");
            return false;
        }

        switch (content.getMembership()) {
            case Event.MembershipState.JOIN:
                LOGGER.debug("Master bot has joined to the room.");
                return true;
            case Event.MembershipState.INVITE:
                LOGGER.info("Invite the master bot");
                addInviter(event.getRoomId(), event.getSender(), handle);
                getMatrixClient().room().joinByIdOrAlias(event.getRoomId()).join();
                return true;
            case Event.MembershipState.BAN:
            case Event.MembershipState.LEAVE:
                if (getMatrixClient().room().joinedRooms().join().contains(event.getRoomId())) {
                    LOGGER.info("Master bot doesn't joined, skip.");
                    return false;
                }

                LOGGER.info("Remove transport.");
                removeInviter(event.getRoomId(), handle);
                Transport transport = getMxTransports().remove(event.getRoomId());
                if (transport != null) {
                    if (LOGGER.isInfoEnabled()) {
                        RoomAlias roomAlias = transport.getRoomAlias();
                        LOGGER.info("Room id: {}", roomAlias.getRoomId());
                        LOGGER.info("Alias: {}", roomAlias.getAlias());
                        LOGGER.info("Conference url: {}", roomAlias.getConferenceJid());
                    }
                    transport.remove(handle);
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
        request.setDirect(false);

        MatrixClient client = getMatrixClient();

        client.room().create(request);
        client.room().joinByIdOrAlias(roomAlias);
    }

    protected void checkMasterBot(Handle handle) {
        String masterUserId = getTransportConfiguration().getMasterUserId();
        AppServerUserDao dao = handle.attach(AppServerUserDao.class);
        String nick = Id.getInstance().localpart(masterUserId);
        if (dao.count(nick) == 0) {
            try {
                RegisterRequest request = new RegisterRequest();
                request.setUsername(nick);
                request.setInitialDeviceDisplayName(nick);
                getMatrixClient().account().register(request);
            } catch (MatrixException e) {
                LOGGER.warn("master user already registered.", e);
            }
            dao.save(nick);
        }
    }

    protected void loadInviters(Handle handle) {
        getInviters().putAll(handle.attach(InviterDao.class).load(handle));
    }

    protected void addInviter(String roomId, String userId, Handle handle) {
        handle.attach(InviterDao.class).save(roomId, userId);
        getInviters().put(roomId, userId);
    }

    protected void removeInviter(String roomId, Handle handle) {
        handle.attach(InviterDao.class).remove(roomId);
        getInviters().remove(roomId);
    }

    protected void createMatrixClient() {
        TransportConfiguration config = getTransportConfiguration();
        this.matrixClient = new AppServiceClient.Builder()
            .requestFactory(new JaxRsRequestFactory(getClient(), config.getMatrixHomeserver()))
            .userId(config.getMasterUserId())
            .accessToken(config.getAccessToken())
            .build();
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
