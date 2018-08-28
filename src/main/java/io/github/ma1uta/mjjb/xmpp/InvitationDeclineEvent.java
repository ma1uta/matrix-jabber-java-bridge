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
import rocks.xmpp.extensions.muc.ChatRoom;

import java.util.EventObject;
import java.util.function.Consumer;

/**
 * The multi-user chat invitation decline event, which is triggered when an invitee declines a multi-user chat invitation.
 *
 * @author Christian Schudt
 * @see ChatRoom#addInvitationDeclineListener(Consumer)
 */
public final class InvitationDeclineEvent extends EventObject {

    private final Jid roomAddress;

    private final Jid invitee;

    private final String reason;

    InvitationDeclineEvent(Object source, Jid roomAddress, Jid invitee, String reason) {
        super(source);
        this.invitee = invitee;
        this.reason = reason;
        this.roomAddress = roomAddress;
    }

    /**
     * Gets the invitee, who declined the invitation.
     *
     * @return The invitee.
     */
    public Jid getInvitee() {
        return invitee;
    }

    /**
     * Gets the room address.
     *
     * @return The room address.
     */
    public Jid getRoomAddress() {
        return roomAddress;
    }


    /**
     * Gets the reason for the decline.
     *
     * @return The reason.
     */
    public String getReason() {
        return reason;
    }
}
