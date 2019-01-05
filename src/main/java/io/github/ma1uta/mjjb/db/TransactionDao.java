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

import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.LocalDateTime;

public interface TransactionDao {

    @SqlQuery("select count(*) from transaction where id = :txnId")
    int exist(String txnId);

    @SqlUpdate("insert into transaction(id, started) values(:txnId, :started)")
    void start(String txnId, LocalDateTime started);

    @SqlUpdate("update transaction set processed = :processed where id = :txnId")
    void finish(String txnid, LocalDateTime processed);
}
