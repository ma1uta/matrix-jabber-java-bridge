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
import rocks.xmpp.extensions.disco.model.info.Identity;
import rocks.xmpp.extensions.muc.ChatRoom;
import rocks.xmpp.extensions.muc.model.MucFeature;
import rocks.xmpp.extensions.muc.model.RoomInfo;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Represents information about a chat room.
 * <br>
 * This class wraps the service discovery information result, which usually consists of identities, features and extended forms
 * into one class, so that a developer doesn't have to deal with the complex structure of the service discovery result.
 *
 * @author Christian Schudt
 * @see <a href="https://xmpp.org/extensions/xep-0045.html#disco-roominfo">6.4 Querying for Room Information</a>
 * @see ChatRoom#getRoomInformation()
 */
public final class RoomInformation {

    private final Identity identity;

    private final Set<MucFeature> features;

    private final RoomInfo roomInfo;

    RoomInformation(Identity identity, Set<MucFeature> features, RoomInfo roomInfo) {
        this.identity = identity;
        this.features = features;
        this.roomInfo = roomInfo;
    }

    /**
     * Gets the name of the room.
     *
     * @return The room name.
     */
    public String getName() {
        return identity != null ? identity.getName() : null;
    }

    /**
     * Gets the maximum number of history messages returned by the room.
     *
     * @return The maximum number of history messages returned by the room.
     */
    public int getMaxHistoryMessages() {
        if (roomInfo != null) {
            return roomInfo.getMaxHistoryMessages();
        }
        return 0;
    }

    /**
     * Gets the contact addresses (normally, room owner or owners).
     *
     * @return The contact addresses.
     */
    public List<Jid> getContacts() {
        if (roomInfo != null) {
            return roomInfo.getContacts();
        }
        return Collections.emptyList();
    }

    /**
     * Gets a short description.
     *
     * @return The description.
     */
    public String getDescription() {
        if (roomInfo != null) {
            return roomInfo.getDescription();
        }
        return null;
    }

    /**
     * Gets the natural language for room discussions.
     *
     * @return The language.
     */
    public Locale getLanguage() {
        if (roomInfo != null) {
            return roomInfo.getLanguage();
        }
        return null;
    }

    /**
     * Gets an associated LDAP group that defines
     * room membership; this should be an LDAP
     * Distinguished Name according to an
     * implementation-specific or
     * deployment-specific definition of a
     * group.
     *
     * @return The LDAP group.
     */
    public String getLdapGroup() {
        if (roomInfo != null) {
            return roomInfo.getLdapGroup();
        }
        return null;
    }

    /**
     * Gets an URL for archived discussion logs.
     *
     * @return The URL.
     */
    public URL getLogs() {
        if (roomInfo != null) {
            return roomInfo.getLogs();
        }
        return null;
    }

    /**
     * Gets the current number of occupants in the room.
     *
     * @return The number of occupants.
     */
    public Integer getCurrentNumberOfOccupants() {
        if (roomInfo != null) {
            return roomInfo.getCurrentNumberOfOccupants();
        }
        return null;
    }

    /**
     * Gets the current discussion topic.
     *
     * @return The topic.
     */
    public String getSubject() {
        if (roomInfo != null) {
            return roomInfo.getSubject();
        }
        return null;
    }

    /**
     * Indicates, whether the room subject can be modified by participants.
     *
     * @return Whether the room subject can be modified by participants.
     */
    public boolean isChangeSubjectAllowed() {
        return roomInfo != null && roomInfo.isChangeSubjectAllowed();
    }


    // https://xmpp.org/extensions/xep-0045.html#registrar-features

    /**
     * Hidden room.
     *
     * @return If the room is hidden.
     */
    public boolean isHidden() {
        return features.contains(MucFeature.HIDDEN);
    }

    /**
     * Members-only room.
     *
     * @return If the room is members-only.
     */
    public boolean isMembersOnly() {
        return features.contains(MucFeature.MEMBERS_ONLY);
    }

    /**
     * Moderated room.
     *
     * @return If the room is moderated.
     */
    public boolean isModerated() {
        return features.contains(MucFeature.MODERATED);
    }

    /**
     * Non-anonymous room.
     *
     * @return If the room is non-anonymous.
     */
    public boolean isNonAnonymous() {
        return features.contains(MucFeature.NON_ANONYMOUS);
    }

    /**
     * Open room.
     *
     * @return If the room is open.
     */
    public boolean isOpen() {
        return features.contains(MucFeature.OPEN);
    }

    /**
     * Password-protected room.
     *
     * @return If the room is password-protected.
     */
    public boolean isPasswordProtected() {
        return features.contains(MucFeature.PASSWORD_PROTECTED);
    }

    /**
     * Persistent room.
     *
     * @return If the room is persistent.
     */
    public boolean isPersistent() {
        return features.contains(MucFeature.PERSISTENT);
    }

    /**
     * Public room.
     *
     * @return If the room is public.
     */
    public boolean isPublic() {
        return features.contains(MucFeature.PUBLIC);
    }

    /**
     * Semi-anonymous room.
     *
     * @return If the room is semi-anonymous.
     */
    public boolean isSemiAnonymous() {
        return features.contains(MucFeature.SEMI_ANONYMOUS);
    }

    /**
     * Temporary room.
     *
     * @return If the room is temporary.
     */
    public boolean isTemporary() {
        return features.contains(MucFeature.TEMPORARY);
    }

    /**
     * Unmoderated room.
     *
     * @return If the room is unmoderated.
     */
    public boolean isUnmoderated() {
        return features.contains(MucFeature.UNMODERATED);
    }

    /**
     * Unsecured room.
     *
     * @return If the room is unsecured.
     */
    public boolean isUnsecured() {
        return features.contains(MucFeature.UNSECURED);
    }
}
