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

import static io.github.ma1uta.matrix.client.model.presence.PresenceStatus.PresenceType.OFFLINE;
import static io.github.ma1uta.matrix.client.model.presence.PresenceStatus.PresenceType.ONLINE;
import static io.github.ma1uta.matrix.client.model.presence.PresenceStatus.PresenceType.UNAVAILABLE;
import static io.github.ma1uta.matrix.event.Event.MembershipState.BAN;
import static io.github.ma1uta.matrix.event.Event.MembershipState.JOIN;
import static io.github.ma1uta.matrix.event.Event.MembershipState.LEAVE;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import io.github.ma1uta.matrix.client.AppServiceClient;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.factory.jaxrs.JaxRsRequestFactory;
import io.github.ma1uta.matrix.client.model.account.RegisterRequest;
import io.github.ma1uta.matrix.client.model.presence.PresenceStatus;
import io.github.ma1uta.matrix.event.Presence;
import io.github.ma1uta.matrix.event.RoomEvent;
import io.github.ma1uta.matrix.event.RoomMember;
import io.github.ma1uta.matrix.event.RoomMessage;
import io.github.ma1uta.matrix.event.content.RoomMemberContent;
import io.github.ma1uta.matrix.event.content.RoomMessageContent;
import io.github.ma1uta.matrix.event.message.Text;
import io.github.ma1uta.matrix.impl.exception.MatrixException;
import io.github.ma1uta.mjjb.dao.AppServerUserDao;
import io.github.ma1uta.mjjb.dao.RoomAliasDao;
import io.github.ma1uta.mjjb.model.RoomAlias;
import io.github.ma1uta.mjjb.xmpp.ChatRoom;
import io.github.ma1uta.mjjb.xmpp.MultiUserChatManager;
import io.github.ma1uta.mjjb.xmpp.OccupantEvent;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stanza.MessageEvent;
import rocks.xmpp.core.stanza.PresenceEvent;
import rocks.xmpp.core.stanza.model.Message;
import rocks.xmpp.extensions.component.accept.ExternalComponent;

import java.io.Closeable;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.client.Client;

/**
 * Transport (matrix-&gt;xmpp and xmpp-&gt;matrix).
 */
public class Transport implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Transport.class);

    private ExternalComponent xmppComponent;

    private AppServiceClient matrixComponent;

    private RoomAlias roomAlias;

    private String masterUserId;

    private String masterNick;

    private Jdbi jdbi;

    private BiMap<String, String> mxToXmppUsers = HashBiMap.create();

    private BiMap<String, String> xmppToMxUsers = HashBiMap.create();

    private Map<String, ChatRoom> chatRooms = new HashMap<>();

    private String prefix;

    public Transport(TransportConfiguration configuration, ExternalComponent xmppComponent, Client httpClient, RoomAlias roomAlias,
                     Jdbi jdbi) {
        this.xmppComponent = xmppComponent;
        this.matrixComponent = new AppServiceClient.Builder()
            .requestFactory(new JaxRsRequestFactory(httpClient, configuration.getMatrixHomeserver()))
            .userId(configuration.getMasterUserId()).accessToken(configuration.getAccessToken()).build();
        this.roomAlias = roomAlias;
        this.masterUserId = configuration.getMasterUserId();
        this.masterNick = nickInXmpp(masterUserId);
        this.jdbi = jdbi;
        this.prefix = configuration.getPrefix();
    }

    public ExternalComponent getXmppComponent() {
        return xmppComponent;
    }

    public AppServiceClient getMatrixComponent() {
        return matrixComponent;
    }

    public RoomAlias getRoomAlias() {
        return roomAlias;
    }

    /**
     * Find the chat room of the specified nick.
     *
     * @param nick xmpp nick
     * @return chat room.
     */
    public ChatRoom getChatRoom(String nick) {
        return getChatRooms().get(nick);
    }

    public String getMasterUserId() {
        return masterUserId;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public BiMap<String, String> getMxToXmppUsers() {
        return mxToXmppUsers;
    }

    public BiMap<String, String> getXmppToMxUsers() {
        return xmppToMxUsers;
    }

    public String getMasterNick() {
        return masterNick;
    }

    public String getPrefix() {
        return prefix;
    }

    public Map<String, ChatRoom> getChatRooms() {
        return chatRooms;
    }

    /**
     * Initialize transport.
     * <br>
     * Currently connect to the xmpp conference, the matrix room and discover occupants.
     */
    public void init() {
        MultiUserChatManager chatManager = getXmppComponent().getManager(MultiUserChatManager.class);

        ChatRoom chatRoom = chatManager.createChatRoom(Jid.of(getRoomAlias().getConferenceJid()));
        chatRoom.addOccupantListener(this::xmppOccupant);
        getChatRooms().put(getMasterNick(), chatRoom);

        chatRoom.enter(getMasterNick(), null, null, toJid(getMasterNick())).thenAccept(presence -> LOGGER.debug(presence.toString()));
        List<String> occupants;
        try {
            occupants = chatRoom.discoverOccupants().get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Failed discover the conference occupants.", e);
            throw new RuntimeException(e);
        }
        occupants.forEach(this::xmppNickEntered);
        MatrixClient mx = getMatrixComponent();
        if (!mx.room().joinedRooms().join().contains(getRoomAlias().getRoomId())) {
            mx.room().joinByIdOrAlias(getRoomAlias().getRoomId()).join();
        }
        mx.event().joinedMembers(getRoomAlias().getRoomId()).join().getJoined().forEach((userId, roomMember) -> matrixNickEnter(userId));
    }

    /**
     * Process a new xmpp presence.
     *
     * @param presenceEvent the xmpp presence.
     */
    public void xmppPresence(PresenceEvent presenceEvent) {
        String mxUser = getXmppToMxUsers().get(presenceEvent.getPresence().getFrom().getResource());
        if (mxUser == null) {
            return;
        }

        PresenceStatus presence = new PresenceStatus();
        if (presenceEvent.getPresence().getType() == null) {
            presence.setPresence(PresenceStatus.PresenceType.ONLINE);
        } else {
            switch (presenceEvent.getPresence().getType()) {
                case UNAVAILABLE:
                    presence.setPresence(PresenceStatus.PresenceType.UNAVAILABLE);
                    break;
                default:
                    switch (presenceEvent.getPresence().getShow()) {
                        case AWAY:
                        case XA:
                            presence.setPresence(PresenceStatus.PresenceType.OFFLINE);
                            break;
                        case DND:
                            presence.setPresence(PresenceStatus.PresenceType.UNAVAILABLE);
                            break;
                        default:
                            presence.setPresence(PresenceStatus.PresenceType.ONLINE);
                    }
            }
        }
        getMatrixComponent().userId(mxUser).presence().setPresenceStatus(presence).join();
    }

    protected void xmppOccupant(OccupantEvent occupantEvent) {
        String nick = occupantEvent.getOccupant().getNick();
        switch (occupantEvent.getType()) {
            case BANNED:
            case EXITED:
            case KICKED:
                LOGGER.debug("Leave member");
                String mxUser = getXmppToMxUsers().get(nick);
                if (mxUser == null || getMasterNick().equals(nick)) {
                    return;
                }
                getMatrixComponent().userId(mxUser).room().leave(getRoomAlias().getRoomId())
                    .thenRun(() -> getXmppToMxUsers().remove(nick, mxUser)).join();
                break;
            case ENTERED:
                xmppNickEntered(nick);
                break;
            default:
                //Nothing.
        }
    }

    /**
     * Process a new xmpp message.
     *
     * @param messageEvent the new xmpp message.
     */
    public void xmppMessage(MessageEvent messageEvent) {
        Message message = messageEvent.getMessage();
        String mxUser = getXmppToMxUsers().get(message.getFrom().getResource());
        if (mxUser == null || (!getMasterNick().equals(message.getTo().getResource()) && !getMasterNick()
            .equals(message.getTo().getLocal()))) {
            return;
        }

        getMatrixComponent().userId(mxUser).event().sendMessage(getRoomAlias().getRoomId(), message.getBody()).join();
    }

    protected void xmppNickEntered(String xmppNick) {
        try {
            if (getMxToXmppUsers().inverse().get(xmppNick) != null || getMasterNick().equals(xmppNick)) {
                return;
            }

            AppServiceClient mx = getMatrixComponent();
            String displayName = nickInMatrix(xmppNick);
            String localpart = getPrefix() + displayName;
            String userId = "@" + localpart + ":" + new URL(getMatrixComponent().getHomeserverUrl()).getHost();
            getJdbi().useTransaction(handle -> {
                AppServerUserDao dao = handle.attach(AppServerUserDao.class);
                if (dao.count(localpart) == 0) {
                    try {
                        RegisterRequest registerRequest = new RegisterRequest();
                        registerRequest.setUsername(localpart);
                        registerRequest.setInitialDeviceDisplayName(displayName);
                        mx.account().register(registerRequest).join();
                        mx.userId(userId).profile().setDisplayName(displayName).join();
                    } catch (MatrixException e) {
                        LOGGER.warn("Failed to register new user.", e);
                    }
                    dao.save(localpart);
                }

            });
            getXmppToMxUsers().put(xmppNick, userId);

            AppServiceClient userClient = mx.userId(userId);
            userClient.profile().setDisplayName(displayName).join();
            userClient.room().joinByIdOrAlias(getRoomAlias().getRoomId()).join();
        } catch (Exception e) {
            LOGGER.error("Cannot fetch occupants", e);
        }
    }

    protected void matrixNickEnter(String userId) {
        String nick = nickInXmpp(userId);
        try {
            if (getXmppToMxUsers().inverse().get(userId) != null || getMasterUserId().equals(userId)) {
                return;
            }

            getMxToXmppUsers().put(userId, nick);

            ChatRoom chatRoom = getXmppComponent().getManager(MultiUserChatManager.class)
                .createChatRoom(Jid.of(getRoomAlias().getConferenceJid()));
            chatRoom.enter(nick, null, null, toJid(nick)).thenAccept(presence -> LOGGER.debug(presence.toString()));
            getChatRooms().put(nick, chatRoom);
        } catch (Exception e) {
            LOGGER.error(String.format("%s cannot join with nick %s", userId, nick), e);
        }
    }

    protected String nickInXmpp(String userId) {
        String nick = getMatrixComponent().userId(userId).profile().showDisplayName(userId).join();
        if (getXmppToMxUsers().keySet().contains(nick)) {
            nick = nick + "#" + new Random().nextInt();
        }
        return nick;
    }

    protected String nickInMatrix(String nick) {
        return nick;
    }

    protected Jid toJid(String nick) {
        return Jid.of(nick, getXmppComponent().getDomain(), nick);
    }

    /**
     * Translate matrix event to the xmpp message.
     *
     * @param event event.
     */
    public void event(RoomEvent event) {
        String sender = event.getSender();
        String nick = getMxToXmppUsers().get(sender);
        if (getMasterUserId().equals(sender)) {
            LOGGER.debug("Event sent by master bot, skip.");
            return;
        }
        if (nick == null && !(event instanceof RoomMember)) {
            if (LOGGER.isWarnEnabled()) {
                if (getXmppToMxUsers().inverse().get(sender) != null) {
                    LOGGER.debug("Event sent by puppet user, skip.");
                } else {
                    LOGGER.warn("Cannot found the puppet user: {}", sender);
                }
            }
            return;
        }

        Jid jid = toJid(nick);
        LOGGER.debug("Jid: {}", jid);
        if (event instanceof RoomMessage) {
            matrixMessage((RoomMessage) event, jid);
        } else if (event instanceof RoomMember) {
            matrixOccupant((RoomMember) event);
        }
    }

    public void reEnter(String xmppUser) {

    }

    /**
     * Remove this transport.
     */
    public void remove(Handle handle) {
        MatrixClient matrixClient = getMatrixComponent();
        close();
        try {
            matrixClient.room().delete(getRoomAlias().getAlias()).join();
        } catch (MatrixException e) {
            LOGGER.error("Cannot delete the alias", e);
        }
        if (matrixClient.room().joinedRooms().join().contains(getRoomAlias().getRoomId())) {
            matrixClient.room().leave(getRoomAlias().getRoomId());
        }
        handle.attach(RoomAliasDao.class).delete(getRoomAlias().getRoomId());
    }

    protected void matrixMessage(RoomMessage event, Jid jid) {
        LOGGER.debug("m.room.message");
        RoomMessageContent content = event.getContent();
        LOGGER.debug("msgtype: {}", content.getMsgtype());

        if (content instanceof Text) {
            text((Text) content, jid);
        }
    }

    protected void text(Text text, Jid jid) {
        Message message = new Message(null, null, text.getBody());
        message.setFrom(jid);
        getChatRoom(jid.getLocal()).sendMessage(message);
    }

    protected void matrixOccupant(RoomMember event) {
        RoomMemberContent content = event.getContent();
        String stateKey = event.getStateKey();
        switch (content.getMembership()) {
            case JOIN:
                LOGGER.debug("Join new member");
                matrixNickEnter(stateKey);
                break;
            case LEAVE:
            case BAN:
                LOGGER.debug("Leave member");
                String nick = getMxToXmppUsers().get(stateKey);
                if (getMasterUserId().equals(stateKey)) {
                    LOGGER.debug("Master bot, skip");
                    return;
                }
                if (nick == null) {
                    LOGGER.warn("Not found the puppet user: {}", stateKey);
                    return;
                }

                getChatRooms().remove(nick).exit(toJid(nick));
                getMxToXmppUsers().remove(stateKey, nick);
                break;
            default:
                //Nothing.
        }
    }

    protected void matrixPresence(Presence event, Jid jid) {
        LOGGER.debug("m.presence");
        rocks.xmpp.core.stanza.model.Presence xmppPresence = new rocks.xmpp.core.stanza.model.Presence();
        xmppPresence.setFrom(jid);
        switch (event.getContent().getPresence()) {
            case ONLINE:
                LOGGER.debug("online: slip");
                break;
            case OFFLINE:
                LOGGER.debug("offline");
                xmppPresence.setShow(rocks.xmpp.core.stanza.model.Presence.Show.AWAY);
                break;
            case UNAVAILABLE:
                LOGGER.debug("unavailable");
                xmppPresence.setType(rocks.xmpp.core.stanza.model.Presence.Type.UNAVAILABLE);
                xmppPresence.setShow(rocks.xmpp.core.stanza.model.Presence.Show.DND);
                break;
            default:
                //Nothing.
        }
        getXmppComponent().sendPresence(xmppPresence);
    }

    @Override
    public void close() {
        AppServiceClient mx = getMatrixComponent();
        getXmppToMxUsers().values().forEach(userId -> mx.userId(userId).room().leave(getRoomAlias().getRoomId()));
        getXmppToMxUsers().clear();

        getMxToXmppUsers().values().forEach(nick -> getChatRoom(nick).exit(toJid(nick)));
        getMxToXmppUsers().clear();
        getChatRoom(getMasterNick()).exit(toJid(getMasterNick()));
    }
}
