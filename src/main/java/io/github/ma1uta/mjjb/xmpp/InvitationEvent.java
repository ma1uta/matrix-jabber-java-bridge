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
import rocks.xmpp.core.stanza.model.Message;
import rocks.xmpp.extensions.muc.MultiUserChatManager;
import rocks.xmpp.extensions.muc.model.user.MucUser;

import java.util.EventObject;
import java.util.function.Consumer;

/**
 * The multi-user chat invitation event, which is triggered upon receiving an invitation to a multi-user chat.
 *
 * @author Christian Schudt
 * @see MultiUserChatManager#addInvitationListener(Consumer)
 */
public final class InvitationEvent extends EventObject {

    private final Jid inviter;

    private final boolean abContinue;

    private final Jid room;

    private final String password;

    private final String reason;

    private final String thread;

    private final boolean mediated;

    private final XmppSession xmppSession;

    InvitationEvent(Object source, XmppSession xmppSession, Jid inviter, Jid room, String reason, String password, boolean abContinue,
                    String thread, boolean mediated) {
        super(source);
        this.inviter = inviter;
        this.room = room;
        this.reason = reason;
        this.password = password;
        this.abContinue = abContinue;
        this.thread = thread;
        this.mediated = mediated;
        this.xmppSession = xmppSession;
    }

    /**
     * Declines the invitation.
     *
     * @param reason The reason.
     */
    public void decline(String reason) {
        // For direct invitations:
        // If the contact declines the invitation, it shall silently discard the invitation.

        // Therefore only decline mediated invitations.
        if (mediated) {
            Message message = new Message(room);
            message.addExtension(MucUser.withDecline(inviter, reason));
            xmppSession.send(message);
        }
    }

    /**
     * Gets the inviter. If the invitation was mediated by the chat room, the inviter can either be a bare or full JID or the in-room
     * JID of an occupant.
     *
     * @return The inviter.
     */
    public Jid getInviter() {
        return inviter;
    }

    /**
     * Indicates, whether a one-to-one chat session is continued in the chat room.
     *
     * @return If a one-to-one chat session is continued in the chat room.
     * @see #getThread()
     */
    public boolean isContinue() {
        return abContinue;
    }

    /**
     * Gets the room address.
     *
     * @return The room address.
     */
    public Jid getRoomAddress() {
        return room;
    }

    /**
     * Gets the password to the room.
     *
     * @return The password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Gets the reason for the invitation.
     *
     * @return The reason.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Gets the thread of the continued one-to-one chat session (if any).
     *
     * @return The thread.
     * @see #isContinue()
     */
    public String getThread() {
        return thread;
    }

    /**
     * Indicates, whether the invitation is a mediated or direct invitation.
     *
     * @return True, if the invitation was mediated by the room; false, if it is a direct invitation.
     * @see <a href="https://xmpp.org/extensions/xep-0045.html#invite-direct">7.8.1 Direct Invitation</a>
     * @see <a href="https://xmpp.org/extensions/xep-0045.html#invite-mediated">7.8.2 Mediated Invitation</a>
     */
    public boolean isMediated() {
        return mediated;
    }
}
