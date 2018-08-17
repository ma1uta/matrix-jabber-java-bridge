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
import io.github.ma1uta.mjjb.model.AppServerUser;
import org.hibernate.SessionFactory;

import javax.persistence.NoResultException;

/**
 * DAO for persist and check application service users.
 */
public class AppServerUserDao extends AbstractDAO<AppServerUser> {

    /**
     * Creates a new DAO with a given session provider.
     *
     * @param sessionFactory a session provider
     */
    public AppServerUserDao(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    /**
     * Save new appService User.
     *
     * @param localpart user localpart.
     */
    public void save(String localpart) {
        AppServerUser user = new AppServerUser();
        user.setLocalpart(localpart);
        super.persist(user);
    }

    /**
     * Find user by localpart.
     *
     * @param localpart localpart.
     * @return user MXID or null.
     */
    public boolean exist(String localpart) {
        try {
            return uniqueResult(
                query("SELECT u FROM AppServerUser u WHERE u.localpart = :localpart").setParameter("localpart", localpart)) != null;
        } catch (NoResultException e) {
            return false;
        }
    }
}
