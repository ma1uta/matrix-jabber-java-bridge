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
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Size;

/**
 * Stored info about integrated rooms.
 */
@Entity
@Table(name = "room_alias")
public class RoomAlias {

    @Id
    @Column(length = 4000)
    @Size(max = 4000)
    private String alias;

    @Column(name = "room_id", length = 1000)
    @Size(max = 1000)
    private String roomId;

    @Column(name = "conference_url", length = 1000)
    @Size(max = 1000)
    private String conferenceUrl;

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

    public String getConferenceUrl() {
        return conferenceUrl;
    }

    public void setConferenceUrl(String conferenceUrl) {
        this.conferenceUrl = conferenceUrl;
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
