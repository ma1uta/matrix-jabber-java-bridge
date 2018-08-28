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
import rocks.xmpp.core.session.XmppSession;
import rocks.xmpp.extensions.disco.ServiceDiscoveryManager;
import rocks.xmpp.extensions.disco.model.items.Item;
import rocks.xmpp.util.concurrent.AsyncResult;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

/**
 * A chat service hosts chat rooms. It allows you to discover public chat rooms or create new (instant) rooms, if allowed by the service.
 * <p>
 * You get an instance of this class by either using the {@link MultiUserChatManager#createChatService(Jid)} method or
 * by {@linkplain MultiUserChatManager#discoverChatServices() discovering} the chat services at your connected domain.
 * </p>
 *
 * @author Christian Schudt
 * @see MultiUserChatManager#createChatService(Jid)
 */
public final class ChatService implements Comparable<ChatService> {

    private final XmppSession xmppSession;

    private final Jid serviceAddress;

    private final String name;

    private final ServiceDiscoveryManager serviceDiscoveryManager;

    private final MultiUserChatManager multiUserChatManager;

    ChatService(Jid serviceAddress, String name, XmppSession xmppSession, ServiceDiscoveryManager serviceDiscoveryManager,
                MultiUserChatManager multiUserChatManager) {
        this.xmppSession = xmppSession;
        this.serviceAddress = serviceAddress;
        this.name = name;
        this.serviceDiscoveryManager = serviceDiscoveryManager;
        this.multiUserChatManager = multiUserChatManager;
    }

    /**
     * Discovers the list of chat rooms hosted by this chat service.
     *
     * @return The async result with the list of public rooms.
     * @see <a href="https://xmpp.org/extensions/xep-0045.html#disco-rooms">6.3 Discovering Rooms</a>
     */
    public AsyncResult<List<ChatRoom>> discoverRooms() {
        return serviceDiscoveryManager.discoverItems(serviceAddress).thenApply(itemNode -> {
            List<ChatRoom> chatRooms = new ArrayList<>();
            for (Item item : itemNode.getItems()) {
                if (item.getJid() != null && item.getJid().getLocal() != null) {
                    ChatRoom chatRoom = new ChatRoom(item.getJid(), item.getName(), xmppSession, serviceDiscoveryManager,
                        multiUserChatManager);
                    chatRooms.add(chatRoom);
                }
            }
            return chatRooms;
        });
    }

    /**
     * Creates a new chat room. Note that this room is only created locally.
     *
     * @param room The room. This is the local part of the room address, e.g. room@service.
     * @return The chat room.
     */
    public ChatRoom createRoom(String room) {
        return new ChatRoom(serviceAddress.withLocal(room), null, xmppSession, serviceDiscoveryManager, multiUserChatManager);
    }

    /**
     * Gets the service address.
     *
     * @return The service address.
     */
    public Jid getAddress() {
        return serviceAddress;
    }

    /**
     * Gets the name of this service.
     *
     * @return The name or null, if the name is unknown.
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return serviceAddress != null ? serviceAddress.toString() : super.toString();
    }

    /**
     * Compares this chat service first by their name and then by their service address.
     *
     * @param o The other chat service.
     * @return The comparison result.
     */
    @Override
    public int compareTo(ChatService o) {
        if (this == o) {
            return 0;
        }
        if (o != null) {
            int result;
            // First compare name.
            if (name != null) {
                result = o.name != null ? Collator.getInstance().compare(name, o.name) : -1;
            } else {
                result = o.name != null ? 1 : 0;
            }
            // If the names are equal, compare addresses.
            if (result == 0) {
                if (serviceAddress != null) {
                    return o.serviceAddress != null ? serviceAddress.compareTo(o.serviceAddress) : -1;
                } else {
                    return o.serviceAddress != null ? 1 : 0;
                }
            }
            return result;
        }
        return -1;
    }
}
