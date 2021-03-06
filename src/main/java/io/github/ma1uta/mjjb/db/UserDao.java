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

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * User DAO.
 */
public interface UserDao {

    /**
     * Save info about new appuser.
     *
     * @param localpart appuser's username.
     */
    @SqlUpdate("insert into app_user(localpart) values(:localpart)")
    void create(@Bind("localpart") String localpart);

    /**
     * Check appuser availability.
     *
     * @param localpart appuser's username.
     * @return {@code 1} if exists else {@code false}.
     */
    @SqlQuery("select count(*) from app_user where localpart = :localpart")
    int exist(@Bind("localpart") String localpart);
}
