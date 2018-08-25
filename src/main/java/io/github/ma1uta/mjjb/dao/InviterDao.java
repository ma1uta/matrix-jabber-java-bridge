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

import org.apache.commons.lang3.tuple.Pair;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * DAO for persist and user who invited the bot.
 */
public interface InviterDao {

    /**
     * Save new inviter.
     *
     * @param roomId the room where the bot was invited.
     * @param userId who invited the bot.
     */
    @SqlUpdate("insert into inviter(room_id, user_id) values(:roomId, :userId)")
    void save(@Bind("roomId") String roomId, @Bind("userId") String userId);

    /**
     * Remove the inviter.
     *
     * @param roomId the room where the bot was invited.
     */
    @SqlUpdate("delete from inviter where room_id = :roomId")
    void remove(@Bind("roomId") String roomId);

    /**
     * Load all inviters.
     *
     * @param handle handle to invoke query.
     * @return inviters.
     */
    default Map<String, String> load(Handle handle) {
        return handle.createQuery("select room_id, user_id from inviter")
            .map((rs, ctx) -> Pair.of(rs.getString("room_id"), rs.getString("user_id")))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
}
