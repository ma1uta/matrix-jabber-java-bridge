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

import rocks.xmpp.extensions.muc.ChatRoom;

import java.time.Instant;
import java.util.EventObject;
import java.util.function.Consumer;

/**
 * This event is fired, when the subject in a chat room has changed.
 *
 * @author Christian Schudt
 * @see ChatRoom#addSubjectChangeListener(Consumer)
 */
public final class SubjectChangeEvent extends EventObject {
    private final String subject;

    private final Instant date;

    private final boolean isDelayed;

    private final String nickname;

    /**
     * Constructs a prototypical Event.
     *
     * @param source  The object on which the Event initially occurred.
     * @param subject The subject.
     * @throws IllegalArgumentException if source is null.
     */
    SubjectChangeEvent(Object source, String subject, String nick, boolean isDelayed, Instant date) {
        super(source);
        this.subject = subject;
        this.isDelayed = isDelayed;
        this.date = date;
        this.nickname = nick;
    }

    /**
     * Gets the new subject.
     *
     * @return The subject.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Gets the date, when the message was sent.
     *
     * @return The send date.
     */
    public Instant getDate() {
        return date;
    }

    /**
     * Gets the nickname who changed the subject.
     *
     * @return The nickname.
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Indicates, if the subject change is delayed.
     *
     * @return True, if the subject change is delayed.
     */
    public boolean isDelayed() {
        return isDelayed;
    }
}
