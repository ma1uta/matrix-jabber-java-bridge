/*
 * Copyright sablintolya@gmai.com
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

package io.github.ma1uta.mjjb.db;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Room DAO.
 */
public interface RoomDao {

    /**
     * Find 1:1 room be id.
     *
     * @param roomId room id.
     * @return Room or @{code null}.
     */
    @SqlQuery("select * from direct_room where room_id = :roomId")
    @RegisterRowMapper(DirectRoomMapper.class)
    DirectRoom findDirectRoom(@Bind("roomId") String roomId);

    /**
     * Save info about the 1:1 room.
     *
     * @param roomId     room id.
     * @param matrixUser matrix participant.
     * @param xmppUser   xmpp participant.
     * @return saved info.
     */
    @SqlUpdate("insert into direct_room(room_id, matrix_user, xmpp_user) values(:roomId, :matrixUser, :xmppUser)")
    @GetGeneratedKeys
    @RegisterRowMapper(DirectRoomMapper.class)
    DirectRoom createDirectRoom(@Bind("roomId") String roomId, @Bind("matrixUser") String matrixUser, @Bind("xmppUser") String xmppUser);

    /**
     * Update subscription of a matrix user.
     *
     * @param roomId       room id.
     * @param subscription subscribed or not.
     */
    @SqlUpdate("update direct_room set matrix_subs = :subscription where room_id = :roomId")
    void updateMatrixSubscription(@Bind("roomId") String roomId, @Bind("subscription") boolean subscription);

    /**
     * Update subscription of a xmpp user.
     *
     * @param roomId       room id.
     * @param subscription subscribed or not.
     */
    @SqlUpdate("update direct_room set xmpp_subs = :subscription where room_id = :roomId")
    void updateXmppSubscription(@Bind("roomId") String roomId, @Bind("subscription") boolean subscription);
}
