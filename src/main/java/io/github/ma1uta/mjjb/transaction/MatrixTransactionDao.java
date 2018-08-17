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

package io.github.ma1uta.mjjb.transaction;

import io.dropwizard.hibernate.AbstractDAO;
import io.github.ma1uta.matrix.appservice.TransactionDao;
import org.hibernate.SessionFactory;

/**
 * DAO for transactions.
 */
public class MatrixTransactionDao extends AbstractDAO<MatrixTransaction> implements TransactionDao<MatrixTransaction> {

    /**
     * Creates a new DAO with a given session provider.
     *
     * @param sessionFactory a session provider
     */
    public MatrixTransactionDao(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    /**
     * Save new transaction.
     *
     * @param transaction transaction.
     */
    @Override
    public void save(MatrixTransaction transaction) {
        persist(transaction);
    }

    /**
     * Check the specified transaction exists.
     *
     * @param txnId transaction id.
     * @return {@code true} if exist else {@code false}.
     */
    @Override
    public boolean exist(String txnId) {
        return get(txnId) != null;
    }
}
