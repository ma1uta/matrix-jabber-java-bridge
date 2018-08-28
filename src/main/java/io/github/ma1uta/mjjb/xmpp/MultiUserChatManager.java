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

package io.github.ma1uta.mjjb.xmpp;

import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.session.Manager;
import rocks.xmpp.core.session.XmppSession;
import rocks.xmpp.core.stanza.MessageEvent;
import rocks.xmpp.core.stanza.model.Message;
import rocks.xmpp.extensions.disco.ServiceDiscoveryManager;
import rocks.xmpp.extensions.disco.model.info.Identity;
import rocks.xmpp.extensions.disco.model.items.Item;
import rocks.xmpp.extensions.disco.model.items.ItemNode;
import rocks.xmpp.extensions.muc.conference.model.DirectInvitation;
import rocks.xmpp.extensions.muc.model.user.Invite;
import rocks.xmpp.extensions.muc.model.user.MucUser;
import rocks.xmpp.extensions.rsm.ResultSetProvider;
import rocks.xmpp.util.XmppUtils;
import rocks.xmpp.util.concurrent.AsyncResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages Multi-User Chat.
 *
 * @author Christian Schudt
 * @see <a href="https://xmpp.org/extensions/xep-0045.html">XEP-0045: Multi-User Chat</a>
 */
public final class MultiUserChatManager extends Manager {

    private static final String ROOMS_NODE = "http://jabber.org/protocol/muc#rooms";

    private final ServiceDiscoveryManager serviceDiscoveryManager;

    private final Set<Consumer<InvitationEvent>> invitationListeners = new CopyOnWriteArraySet<>();

    private final Map<Jid, Item> enteredRoomsMap = new ConcurrentSkipListMap<>();

    private final Consumer<MessageEvent> messageListener;

    private final ResultSetProvider<Item> itemProvider;

    private MultiUserChatManager(final XmppSession xmppSession) {
        super(xmppSession, true);
        this.serviceDiscoveryManager = xmppSession.getManager(ServiceDiscoveryManager.class);
        this.messageListener = e -> {
            Message message = e.getMessage();
            // Check, if the message contains a mediated invitation.
            MucUser mucUser = message.getExtension(MucUser.class);
            if (mucUser != null) {
                for (Invite invite : mucUser.getInvites()) {
                    XmppUtils.notifyEventListeners(invitationListeners,
                        new InvitationEvent(MultiUserChatManager.this, xmppSession, invite.getFrom(), message.getFrom(), invite.getReason(),
                            mucUser.getPassword(), invite.isContinue(), invite.getThread(), true));
                }
            } else {
                // Check, if the message contains a direct invitation.
                DirectInvitation directInvitation = message.getExtension(DirectInvitation.class);
                if (directInvitation != null) {
                    XmppUtils.notifyEventListeners(invitationListeners,
                        new InvitationEvent(MultiUserChatManager.this, xmppSession, message.getFrom(), directInvitation.getRoomAddress(),
                            directInvitation.getReason(), directInvitation.getPassword(), directInvitation.isContinue(),
                            directInvitation.getThread(), false));
                }
            }
        };
        itemProvider = ResultSetProvider.forItems(enteredRoomsMap.values());
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        // Listen for inbound invitations.
        xmppSession.addInboundMessageListener(messageListener);
        serviceDiscoveryManager.setItemProvider(ROOMS_NODE, itemProvider);
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        xmppSession.removeInboundMessageListener(messageListener);
        serviceDiscoveryManager.setItemProvider(ROOMS_NODE, null);
    }

    /**
     * Adds an invitation listener, which allows to listen for inbound multi-user chat invitations.
     *
     * @param invitationListener The listener.
     * @see #removeInvitationListener(Consumer)
     */
    public void addInvitationListener(Consumer<InvitationEvent> invitationListener) {
        invitationListeners.add(invitationListener);
    }

    /**
     * Removes a previously added invitation listener.
     *
     * @param invitationListener The listener.
     * @see #addInvitationListener(Consumer)
     */
    public void removeInvitationListener(Consumer<InvitationEvent> invitationListener) {
        invitationListeners.remove(invitationListener);
    }

    /**
     * Discovers the multi-user chat services hosted at the connected domain.
     *
     * @return The async result with the list of chat services.
     * @see <a href="https://xmpp.org/extensions/xep-0045.html#disco-service">6.1 Discovering a MUC Service</a>
     */
    public AsyncResult<List<ChatService>> discoverChatServices() {
        return serviceDiscoveryManager.discoverServices(Identity.conferenceText()).thenApply(services ->
            services.stream()
                .map(service -> new ChatService(service.getJid(), service.getName(), xmppSession, serviceDiscoveryManager, this))
                .collect(Collectors.toList()));
    }

    /**
     * Discovers the rooms, where a contact is in.
     *
     * @param contact The contact, which must be a full JID.
     * @return The async result with the items, {@link Item#getJid()} has the room address, and {@link Item#getName()}} has the nickname.
     * @see <a href="https://xmpp.org/extensions/xep-0045.html#disco-client">6.7 Discovering Client Support for MUC</a>
     */
    public AsyncResult<List<Item>> discoverEnteredRooms(Jid contact) {
        return serviceDiscoveryManager.discoverItems(contact, ROOMS_NODE).thenApply(ItemNode::getItems);
    }

    /**
     * Creates a chat service for the specified service address.
     *
     * @param chatService The chat service address. Usually this is hosted at the subdomain "conference".
     * @return The chat service.
     */
    public ChatService createChatService(Jid chatService) {
        return new ChatService(chatService, null, xmppSession, serviceDiscoveryManager, this);
    }

    /**
     * Creates a chat room for the specified room address.
     *
     * @param roomAddress The chat room address.
     * @return The chat room.
     */
    public ChatRoom createChatRoom(Jid roomAddress) {
        return new ChatRoom(roomAddress, null, xmppSession, serviceDiscoveryManager, this);
    }

    void roomEntered(ChatRoom chatRoom, String nick) {
        enteredRoomsMap.put(chatRoom.getAddress(), new Item(chatRoom.getAddress(), null, nick));
    }

    void roomExited(ChatRoom chatRoom) {
        enteredRoomsMap.remove(chatRoom.getAddress());
    }

    @Override
    protected void dispose() {
        invitationListeners.clear();
    }
}
