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

package io.github.ma1uta.mjjb;

import io.github.ma1uta.matrix.event.Event;
import io.github.ma1uta.mjjb.config.AppConfig;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.core.stanza.model.Stanza;

import java.lang.reflect.ParameterizedType;

/**
 * Router factory.
 */
public class RouterFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouterFactory.class);

    private final AppConfig config;
    private final Jdbi jdbi;

    private MultiValuedMap<Class, AbstractRouter<Event>> matrixRouters = new ArrayListValuedHashMap<>();
    private MultiValuedMap<Class, AbstractRouter<Stanza>> xmppRouters = new ArrayListValuedHashMap<>();

    public RouterFactory(AppConfig config, Jdbi jdbi) {
        this.config = config;
        this.jdbi = jdbi;
    }

    public AppConfig getConfig() {
        return config;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    /**
     * Add matrix routers.
     *
     * @param routers new matrix routers.
     */
    @SuppressWarnings("unchecked")
    public void addMatrixRouter(AbstractRouter<? extends Event>... routers) {
        for (AbstractRouter<? extends Event> router : routers) {
            Class<?> key = (Class<?>) ((ParameterizedType) router.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
            getMatrixRouters().put(key, (AbstractRouter<Event>) router);
        }
    }

    public MultiValuedMap<Class, AbstractRouter<Event>> getMatrixRouters() {
        return matrixRouters;
    }

    /**
     * Add xmpp routers.
     *
     * @param router new xmpp routers.
     */
    @SuppressWarnings("unchecked")
    public void addXmppRouter(AbstractRouter<? extends Stanza> router) {
        Class<?> key = (Class<?>) ((ParameterizedType) router.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        getXmppRouters().put(key, (AbstractRouter<Stanza>) router);
    }

    public MultiValuedMap<Class, AbstractRouter<Stanza>> getXmppRouters() {
        return xmppRouters;
    }

    /**
     * Process Matrix event.
     *
     * @param event event.
     */
    public void process(Event event) {
        for (AbstractRouter<Event> router : getMatrixRouters().get(event.getClass())) {
            if (router.apply(event)) {
                break;
            }
        }
    }

    /**
     * Process Xmpp stanza.
     *
     * @param stanza stanza.
     */
    public void process(Stanza stanza) {
        for (AbstractRouter<Stanza> router : getXmppRouters().get(stanza.getClass())) {
            if (router.apply(stanza)) {
                break;
            }
        }
    }
}
