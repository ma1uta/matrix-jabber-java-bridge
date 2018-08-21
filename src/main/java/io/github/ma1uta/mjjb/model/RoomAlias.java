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

package io.github.ma1uta.mjjb.model;

import java.util.Objects;

/**
 * Stored info about integrated rooms.
 */
public class RoomAlias {

    private String alias;

    private String roomId;

    private String conferenceJid;

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getConferenceJid() {
        return conferenceJid;
    }

    public void setConferenceJid(String conferenceJid) {
        this.conferenceJid = conferenceJid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoomAlias)) {
            return false;
        }
        RoomAlias roomAlias = (RoomAlias) o;
        return Objects.equals(alias, roomAlias.alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alias);
    }
}
