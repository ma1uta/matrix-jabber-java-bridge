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

import io.dropwizard.hibernate.AbstractDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.mjjb.model.RoomAlias;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.persistence.NoResultException;

/**
 * DAO for get findAll integrated rooms, save new or delete old one.
 */
public class RoomAliasDao extends AbstractDAO<RoomAlias> {

    /**
     * Pattern for the room alias. (#&lt;prefix&gt;_&lt;conference_name&gt;_&lt;xmpp_url&gt;:&lt;homeserver&gt;).
     * The full conference url will be "conference_name@xmpp_url".
     * <br>
     * For example, the room's alias "#xmpp_myconf_conference.jabber.org:matrix.org" is corresponding to the
     * "myconf@conference.jabber.org" conference.
     */
    public static final Pattern ROOM_PATTERN = Pattern.compile("([a-zA-Z0-9.=\\-/]+)_([a-zA-Z0-9.=\\-/]+)_([a-zA-Z0-9.=\\-/]+)");

    private static final int CONFERENCE_INDEX = 2;

    private static final int XMPP_URL_INDEX = 3;

    /**
     * Creates a new DAO with a given session provider.
     *
     * @param sessionFactory a session provider
     */
    public RoomAliasDao(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    /**
     * Find all integrated rooms.
     *
     * @return all integrated rooms.
     */
    @UnitOfWork
    public List<RoomAlias> findAll() {
        return list(query("SELECT r FROM RoomAlias r"));
    }

    /**
     * Save a new alias.
     *
     * @param alias  a new integrated room.
     * @param roomId the room id.
     * @return new integrated room.
     */
    @UnitOfWork
    public RoomAlias persist(String alias, String roomId) {

        String localpart = Id.localpart(alias);
        Matcher matcher = ROOM_PATTERN.matcher(localpart);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Wrong room alias. It should be set as #<prefix>_<conference_name>_<xmpp_url>:<matrix_homeserver>");
        }

        RoomAlias roomAlias = new RoomAlias();
        roomAlias.setAlias(alias);
        roomAlias.setRoomId(roomId);
        roomAlias.setConferenceUrl(matcher.group(CONFERENCE_INDEX) + "@" + matcher.group(XMPP_URL_INDEX));
        return persist(roomAlias);
    }

    @Override
    public RoomAlias persist(RoomAlias roomAlias) {
        return super.persist(roomAlias);
    }

    /**
     * Find integrated room by alias.
     *
     * @param alias alias.
     * @return integrated room or null.
     */
    public RoomAlias findByAlias(String alias) {
        try {
            return uniqueResult(query("SELECT r FROM RoomAlias r WHERE r.alias = :alias").setParameter("alias", alias));
        } catch (NoResultException e) {
            return null;
        }
    }
}
