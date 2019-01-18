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
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface RoomDao {

    @SqlQuery("select * from direct_room where room_id = :roomId")
    @RegisterRowMapper(DirectRoomMapper.class)
    DirectRoom findDirectRoom(String roomId);

    @SqlUpdate("insert into direct_room(room_id, matrix_user, xmpp_user) values(:roomId, :matrixUser, :xmppUser)")
    @GetGeneratedKeys
    @RegisterRowMapper(DirectRoomMapper.class)
    DirectRoom createDirectRoom(String roomId, String matrixUser, String xmppUser);

    @SqlUpdate("update direct_room set matrix_subs = :subscription where room_id = :roomId")
    void updateMatrixSubscription(String roomId, boolean subscription);

    @SqlUpdate("update direct_room set xmpp_subs = :subscription where room_id = :roomId")
    void updateXmppSubscription(String roomId, boolean subscription);
}
