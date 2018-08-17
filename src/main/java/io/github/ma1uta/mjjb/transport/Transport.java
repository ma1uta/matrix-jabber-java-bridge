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

import static io.github.ma1uta.matrix.Event.MembershipState.BAN;
import static io.github.ma1uta.matrix.Event.MembershipState.JOIN;
import static io.github.ma1uta.matrix.Event.MembershipState.LEAVE;
import static io.github.ma1uta.matrix.client.model.presence.PresenceStatus.PresenceType.OFFLINE;
import static io.github.ma1uta.matrix.client.model.presence.PresenceStatus.PresenceType.ONLINE;
import static io.github.ma1uta.matrix.client.model.presence.PresenceStatus.PresenceType.UNAVAILABLE;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import io.github.ma1uta.matrix.Event;
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.model.account.RegisterRequest;
import io.github.ma1uta.matrix.client.model.presence.PresenceStatus;
import io.github.ma1uta.matrix.events.Presence;
import io.github.ma1uta.matrix.events.RoomMember;
import io.github.ma1uta.matrix.events.RoomMessage;
import io.github.ma1uta.matrix.events.messages.Text;
import io.github.ma1uta.mjjb.dao.AppServerUserDao;
import io.github.ma1uta.mjjb.model.RoomAlias;
import io.github.ma1uta.mjjb.xmpp.ExternalComponentWithResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.session.XmppSessionConfiguration;
import rocks.xmpp.core.stanza.MessageEvent;
import rocks.xmpp.core.stanza.PresenceEvent;
import rocks.xmpp.core.stanza.model.Message;
import rocks.xmpp.extensions.muc.ChatRoom;
import rocks.xmpp.extensions.muc.MultiUserChatManager;
import rocks.xmpp.extensions.muc.OccupantEvent;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import javax.ws.rs.client.Client;

/**
 * Transport (matrix-&gt;xmpp and xmpp-&gt;matrix).
 */
public class Transport implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Transport.class);

    private final Object xmppMonitor = new Object();

    private final Object matrixMonitor = new Object();

    private ExternalComponentWithResource xmppComponent;

    private MatrixClient matrixComponent;

    private ChatRoom chatRoom;

    private RoomAlias roomAlias;

    private String masterUserId;

    private String masterNick;

    private PersistenceService service;

    private BiMap<String, String> mxToXmppUsers = HashBiMap.create();

    private BiMap<String, String> xmppToMxUsers = HashBiMap.create();

    public Transport(TransportConfiguration configuration, XmppSessionConfiguration xmppSessionConfiguration, Client httpClient,
                     RoomAlias roomAlias, String masterUserId, PersistenceService service) {
        this.xmppComponent = ExternalComponentWithResource
            .create(configuration.getXmppComponentName(), configuration.getXmppShareSecret(), xmppSessionConfiguration,
                configuration.getXmppHostName(), configuration.getXmppPort());
        this.matrixComponent = new MatrixClient(configuration.getMatrixHomeserver(), httpClient, true, false);
        this.matrixComponent.setAccessToken(configuration.getAccessToken());
        this.roomAlias = roomAlias;
        this.masterUserId = masterUserId;
        this.masterNick = nickInXmpp(masterUserId);
        this.service = service;
    }

    public ExternalComponentWithResource getXmppComponent() {
        return xmppComponent;
    }

    public MatrixClient getMatrixComponent() {
        return matrixComponent;
    }

    public RoomAlias getRoomAlias() {
        return roomAlias;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    protected void setChatRoom(ChatRoom chatRoom) {
        this.chatRoom = chatRoom;
    }

    public String getMasterUserId() {
        return masterUserId;
    }

    public PersistenceService getService() {
        return service;
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

    /**
     * Initialize transport.
     * <br>
     * Currently connect to the xmpp server.
     *
     * @throws XmppException when cannot connect to the xmpp server.
     */
    public void init() throws XmppException {
        initXmpp();
        initTransport();
    }

    protected void initXmpp() {
        ExternalComponentWithResource xmppComponent = getXmppComponent();
        MultiUserChatManager chatManager = xmppComponent.getManager(MultiUserChatManager.class);
        setChatRoom(chatManager.createChatRoom(Jid.of(getRoomAlias().getConferenceUrl())));
    }

    protected void initTransport() throws XmppException {
        ExternalComponentWithResource xmpp = getXmppComponent();

        getChatRoom().addOccupantListener(this::xmppOccupant);
        xmpp.addInboundPresenceListener(this::xmppPresence);
        xmpp.addInboundMessageListener(this::xmppMessage);

        xmpp.connect();

        xmpp.setConnectedResource(toJid(getMasterNick()));
        getChatRoom().enter(getMasterNick());
        getChatRoom().discoverOccupants().thenAccept(occupants -> occupants.forEach(this::xmppNickEntered));

        MatrixClient mx = getMatrixComponent();
        mx.setUserId(getMasterUserId());
        mx.room().joinByIdOrAlias(getRoomAlias().getRoomId());
        mx.event().joinedMembers(getRoomAlias().getRoomId()).getJoined().forEach((userId, roomMember) -> matrixNickEnter(userId));
    }

    protected void xmppPresence(PresenceEvent presenceEvent) {
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
        synchronized (matrixMonitor) {
            MatrixClient mx = getMatrixComponent();
            mx.setUserId(mxUser);
            mx.presence().setPresenceStatus(presence);
        }
    }

    protected void xmppOccupant(OccupantEvent occupantEvent) {
        String nick = occupantEvent.getOccupant().getNick();
        switch (occupantEvent.getType()) {
            case BANNED:
            case EXITED:
            case KICKED:
                String mxUser = getXmppToMxUsers().get(nick);
                if (mxUser == null || getMasterNick().equals(nick)) {
                    return;
                }
                synchronized (matrixMonitor) {
                    MatrixClient mx = getMatrixComponent();
                    mx.setUserId(mxUser);
                    mx.room().leave(getRoomAlias().getRoomId());
                    getXmppToMxUsers().remove(nick, mxUser);
                }
                break;
            case ENTERED:
                xmppNickEntered(nick);
                break;
            default:
                //Nothing.
        }
    }

    protected void xmppMessage(MessageEvent messageEvent) {
        Message message = messageEvent.getMessage();
        String mxUser = getXmppToMxUsers().get(message.getFrom().getResource());
        if (mxUser == null || !getMasterNick().equals(message.getTo().getResource())) {
            return;
        }

        synchronized (matrixMonitor) {
            MatrixClient mx = getMatrixComponent();
            mx.setUserId(mxUser);
            mx.event().sendMessage(getRoomAlias().getRoomId(), message.getBody());
        }
    }

    protected void xmppNickEntered(String xmppNick) {
        try {
            if (getMxToXmppUsers().inverse().get(xmppNick) != null || getMasterNick().equals(xmppNick)) {
                return;
            }

            MatrixClient mx = getMatrixComponent();
            String localpart = nickInMatrix(xmppNick);
            AppServerUserDao userDao = getService().getUserDao();
            String userId;
            synchronized (matrixMonitor) {
                synchronized (userDao) {
                    if (!userDao.exist(localpart)) {
                        RegisterRequest registerRequest = new RegisterRequest();
                        registerRequest.setUsername(localpart);
                        registerRequest.setInitialDeviceDisplayName(localpart);
                        mx.setUserId(getMasterUserId());
                        mx.account().register(registerRequest);
                        mx.profile().setDisplayName(localpart);
                        userDao.save(localpart);
                    }
                    userId = "@" + localpart + ":" + new URL(getMatrixComponent().getHomeserverUrl()).getHost();
                }
                getXmppToMxUsers().put(xmppNick, userId);

                mx.setUserId(localpart);
                mx.room().joinByIdOrAlias(getRoomAlias().getRoomId());
            }
        } catch (Exception e) {
            LOGGER.error("Cannot fetch occupants", e);
        }
    }

    protected void matrixNickEnter(String userId) {
        try {
            if (getXmppToMxUsers().inverse().get(userId) != null || getMasterUserId().equals(userId)) {
                return;
            }

            synchronized (xmppMonitor) {
                String nick = nickInXmpp(userId);
                getMxToXmppUsers().put(userId, nick);

                getXmppComponent().setConnectedResource(toJid(nick));
                getChatRoom().enter(nick);
            }
        } catch (Exception e) {
            LOGGER.error("Cannot fetch occupants", e);
        }
    }

    protected String nickInXmpp(String userId) {
        return Id.localpart(userId);
    }

    protected String nickInMatrix(String nick) {
        return "xmpp_" + nick;
    }

    protected Jid toJid(String nick) {
        return Jid.of(getXmppComponent().getDomain() + "/" + nick);
    }

    /**
     * Translate matrix event to the xmpp message.
     *
     * @param event event.
     */
    public void event(Event event) {
        String sender = event.getSender();
        String nick = getMxToXmppUsers().get(sender);
        if (getMasterUserId().equals(sender) || nick == null) {
            return;
        }

        Jid jid = toJid(nick);
        if (event.getContent() instanceof RoomMessage) {
            matrixMessage(event, jid);
        } else if (event.getContent() instanceof Presence) {
            matrixPresence(event, jid);
        } else if (event.getContent() instanceof RoomMember) {
            matrixOccupant(event);
        }
    }

    protected void matrixMessage(Event event, Jid jid) {
        ExternalComponentWithResource xmpp = getXmppComponent();
        Text text = (Text) event.getContent();
        synchronized (xmppMonitor) {
            xmpp.setConnectedResource(jid);
            getChatRoom().sendMessage(text.getBody());
        }
    }

    protected void matrixOccupant(Event event) {
        RoomMember member = (RoomMember) event.getContent();
        String stateKey = event.getStateKey();
        switch (member.getMembership()) {
            case JOIN:
                matrixNickEnter(stateKey);
                break;
            case LEAVE:
            case BAN:
                String nick = getMxToXmppUsers().get(stateKey);
                if (nick == null || getMasterUserId().equals(stateKey)) {
                    return;
                }

                synchronized (xmppMonitor) {
                    getXmppComponent().setConnectedResource(toJid(nick));
                    getChatRoom().exit();
                    getMxToXmppUsers().remove(stateKey, nick);
                }
                break;
            default:
                //Nothing.
        }
    }

    protected void matrixPresence(Event event, Jid jid) {
        Presence presence = (Presence) event.getContent();
        rocks.xmpp.core.stanza.model.Presence xmppPresence = new rocks.xmpp.core.stanza.model.Presence();
        switch (presence.getPresence()) {
            case ONLINE:
                break;
            case OFFLINE:
                xmppPresence.setShow(rocks.xmpp.core.stanza.model.Presence.Show.AWAY);
                break;
            case UNAVAILABLE:
                xmppPresence.setType(rocks.xmpp.core.stanza.model.Presence.Type.UNAVAILABLE);
                xmppPresence.setShow(rocks.xmpp.core.stanza.model.Presence.Show.DND);
                break;
            default:
                //Nothing.
        }
        synchronized (xmppMonitor) {
            ExternalComponentWithResource xmpp = getXmppComponent();
            xmpp.setConnectedResource(jid);
            xmpp.sendPresence(xmppPresence);
        }
    }

    @Override
    public void close() throws IOException {
        MatrixClient mx = getMatrixComponent();
        mx.setUserId(getMasterUserId());
        getXmppToMxUsers().forEach((nick, userId) -> {
            mx.setUserId(userId);
            mx.room().leave(getRoomAlias().getRoomId());
        });
        getXmppToMxUsers().clear();

        ExternalComponentWithResource xmpp = getXmppComponent();
        getMxToXmppUsers().forEach((userId, nick) -> {
            xmpp.setConnectedResource(toJid(nick));
            getChatRoom().exit();
        });
        getMxToXmppUsers().clear();
        try {
            xmpp.close();
        } catch (XmppException e) {
            LOGGER.error("Cannot close connection", e);
            throw new IOException(e);
        }
    }
}
