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

package io.github.ma1uta.mjjb.dao;

import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.mjjb.model.RoomAlias;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DAO for get findAll integrated rooms, save new or delete old one.
 */
public interface RoomAliasDao {

    /**
     * Pattern for the room alias. (#&lt;prefix&gt;_&lt;conference_name&gt;_&lt;xmpp_url&gt;:&lt;homeserver&gt;).
     * The full conference url will be "conference_name@xmpp_url".
     * <br>
     * For example, the room's alias "#xmpp_myconf_conference.jabber.org:matrix.org" is corresponding to the
     * "myconf@conference.jabber.org" conference.
     */
    Pattern ROOM_PATTERN = Pattern.compile("_([a-zA-Z0-9.=\\-/]+)_([a-zA-Z0-9.=\\-/_]+)_([a-zA-Z0-9.=\\-/]+)");

    /**
     * Index of the conference name.
     */
    int CONFERENCE_INDEX = 2;

    /**
     * Index of the xmpp server.
     */
    int XMPP_URL_INDEX = 3;

    /**
     * Find all integrated rooms.
     *
     * @return all integrated rooms.
     */
    @RegisterBeanMapper(RoomAlias.class)
    @SqlQuery("select room_id, alias, conference_jid from room_alias")
    List<RoomAlias> findAll();

    /**
     * Save a new alias.
     *
     * @param alias         a new integrated room.
     * @param roomId        the room id.
     * @param conferenceJid the conference jid.
     */
    @SqlUpdate("insert into room_alias(room_id, alias, conference_jid) values(:roomId, :alias, :conferenceJid)")
    void persist(@Bind("roomId") String roomId, @Bind("alias") String alias, @Bind("conferenceJid") String conferenceJid);

    /**
     * Save the room alias.
     *
     * @param alias  the room alias.
     * @param roomId the room id.
     * @return the saved room alias.
     */
    default RoomAlias save(String alias, String roomId) {
        String localpart = Id.localpart(alias);
        Matcher matcher = ROOM_PATTERN.matcher(localpart);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Wrong room alias. It should be set as #<prefix>_<conference_name>_<xmpp_url>:<matrix_homeserver>");
        }
        String conferenceJid = matcher.group(CONFERENCE_INDEX) + "@" + matcher.group(XMPP_URL_INDEX);
        return save(alias, roomId, conferenceJid);
    }

    /**
     * Save the room alias.
     *
     * @param alias         the room alias.
     * @param roomId        the room id.
     * @param conferenceJid the mapped conference jid.
     * @return the saved room alias.
     */
    default RoomAlias save(String alias, String roomId, String conferenceJid) {
        persist(roomId, alias, conferenceJid);
        RoomAlias roomAlias = new RoomAlias();
        roomAlias.setRoomId(roomId);
        roomAlias.setAlias(alias);
        roomAlias.setConferenceJid(conferenceJid);
        return roomAlias;
    }

    /**
     * Find integrated room by alias.
     *
     * @param alias alias.
     * @return integrated room or null.
     */
    @RegisterBeanMapper(RoomAlias.class)
    @SqlQuery("select room_id, alias, conference_jid from room_alias where alias = :alias")
    RoomAlias findByAlias(@Bind("alias") String alias);

    /**
     * Delete the pupped room.
     *
     * @param roomId the room id.
     */
    @SqlUpdate("delete from room_alias where room_id = :roomId")
    void delete(@Bind("roomId") String roomId);
}
