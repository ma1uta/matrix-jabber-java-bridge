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

package io.github.ma1uta.mjjb.transport;

import io.dropwizard.hibernate.UnitOfWork;
import io.dropwizard.hibernate.UnitOfWorkAwareProxyFactory;
import io.github.ma1uta.mjjb.dao.AppServerUserDao;
import io.github.ma1uta.mjjb.dao.RoomAliasDao;
import io.github.ma1uta.mjjb.transaction.MatrixTransactionDao;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Service with methods running in the transaction.
 */
public class PersistenceService {

    private final RoomAliasDao aliasDao;

    private final AppServerUserDao userDao;

    private final MatrixTransactionDao txDao;

    private PersistenceService(RoomAliasDao aliasDao, AppServerUserDao userDao, MatrixTransactionDao txDao) {
        this.aliasDao = aliasDao;
        this.userDao = userDao;
        this.txDao = txDao;
    }

    public RoomAliasDao getAliasDao() {
        return aliasDao;
    }

    public AppServerUserDao getUserDao() {
        return userDao;
    }

    public MatrixTransactionDao getTxDao() {
        return txDao;
    }

    /**
     * Execute action within transaction without value returning.
     *
     * @param action action to execute.
     */
    @UnitOfWork
    public void tx(Consumer<PersistenceService> action) {
        action.accept(this);
    }

    /**
     * Execute action within transaction.
     *
     * @param action action to execute.
     * @param <T>    return value type.
     * @return some result.
     */
    @UnitOfWork
    public <T> T tx(Function<PersistenceService, T> action) {
        return action.apply(this);
    }

    public static class Builder {

        private RoomAliasDao aliasDao;
        private AppServerUserDao userDao;
        private MatrixTransactionDao transactionDao;
        private UnitOfWorkAwareProxyFactory proxyFactory;

        /**
         * Add RoomAliasDao.
         *
         * @param aliasDao dao for work with the room aliases.
         * @return builder.
         */
        public Builder aliasDao(RoomAliasDao aliasDao) {
            this.aliasDao = aliasDao;
            return this;
        }

        /**
         * Add PuppetUserDao.
         *
         * @param userDao dao for work with the users.
         * @return builder.
         */
        public Builder userDao(AppServerUserDao userDao) {
            this.userDao = userDao;
            return this;
        }

        /**
         * Add MatrixTransactionDao.
         *
         * @param transactionDao dao for work with the transaction.
         * @return builder.
         */
        public Builder txDao(MatrixTransactionDao transactionDao) {
            this.transactionDao = transactionDao;
            return this;
        }

        /**
         * Add {@link UnitOfWorkAwareProxyFactory}
         *
         * @param proxyFactory proxy to wrap transaction methods.
         * @return builder.
         */
        public Builder proxy(UnitOfWorkAwareProxyFactory proxyFactory) {
            this.proxyFactory = proxyFactory;
            return this;
        }

        /**
         * Build the service.
         *
         * @return service.
         */
        public PersistenceService build() {
            Objects.requireNonNull(aliasDao, "Missing required RoomAliasDao");
            Objects.requireNonNull(userDao, "Missing required PuppetUserDao");
            Objects.requireNonNull(transactionDao, "Missing required MatrixTransactionDao");
            Objects.requireNonNull(proxyFactory, "Missing required UnitOfWorkAwareProxyFactory");

            return proxyFactory.create(PersistenceService.class,
                new Class[] {RoomAliasDao.class, AppServerUserDao.class, MatrixTransactionDao.class},
                new Object[] {aliasDao, userDao, transactionDao});
        }
    }
}
