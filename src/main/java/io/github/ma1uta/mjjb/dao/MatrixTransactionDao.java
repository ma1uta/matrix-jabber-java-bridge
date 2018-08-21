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

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.LocalDateTime;

/**
 * DAO for transactions.
 */
public interface MatrixTransactionDao {

    /**
     * Save new transaction.
     *
     * @param id        transaction id.
     * @param processes timestamp when the transaction was processed.
     */
    @SqlUpdate("insert into transaction(id, processed) values(:id, :processed)")
    void save(@Bind("id") String id, @Bind("processed") LocalDateTime processes);

    /**
     * Check the specified transaction exists.
     *
     * @param id transaction id.
     * @return count of the transactions with the specified id.
     */
    @SqlQuery("select count(*) from transaction where id = :id")
    int exist(@Bind("id") String id);
}
