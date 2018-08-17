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

package io.github.ma1uta.mjjb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import io.dropwizard.Application;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.PooledDataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.hibernate.UnitOfWorkAwareProxyFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.sslreload.SslReloadBundle;
import io.github.ma1uta.jeon.exception.MatrixException;
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.model.account.RegisterRequest;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.transport.PuppetUser;
import io.github.ma1uta.mjjb.model.RoomAlias;
import io.github.ma1uta.mjjb.dao.RoomAliasDao;
import io.github.ma1uta.mjjb.matrix.ApplicationServiceEndpoint;
import io.github.ma1uta.mjjb.transaction.MatrixTransaction;
import io.github.ma1uta.mjjb.transaction.MatrixTransactionDao;
import io.github.ma1uta.mjjb.transport.TransportConfiguration;
import io.github.ma1uta.mjjb.transport.TransportPool;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.core.session.XmppSessionConfiguration;
import rocks.xmpp.core.session.debug.ConsoleDebugger;

import javax.ws.rs.client.Client;

/**
 * Start endpoint.
 */
public class BridgeApplication extends Application<BridgeConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeApplication.class);

    private HibernateBundle<BridgeConfiguration> hibernateBundle = new HibernateBundle<BridgeConfiguration>(RoomAlias.class,
        MatrixTransaction.class, PuppetUser.class) {
        @Override
        public PooledDataSourceFactory getDataSourceFactory(BridgeConfiguration configuration) {
            return configuration.getDatabase();
        }
    };

    /**
     * Start from here.
     *
     * @param args program arguments.
     * @throws Exception when something wrong.
     */
    public static void main(String[] args) throws Exception {
        new BridgeApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<BridgeConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(
            new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
        bootstrap.addBundle(new SslReloadBundle());

        bootstrap.getObjectMapper().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        bootstrap.addBundle(hibernateBundle);
    }

    @Override
    public void run(BridgeConfiguration configuration, Environment environment) {
        environment.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        environment.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Client client = new JerseyClientBuilder(environment).using(configuration.getHttpClient()).build("httpClient");

        XmppSessionConfiguration.Builder xmppSessionBuilder = XmppSessionConfiguration.builder();
        if (configuration.getXmpp().isConsole()) {
            xmppSessionBuilder.debugger(ConsoleDebugger.class);
        }
        XmppSessionConfiguration xmppSessionConfiguration = xmppSessionBuilder.build();

        UnitOfWorkAwareProxyFactory proxyFactory = new UnitOfWorkAwareProxyFactory(hibernateBundle);
        RoomAliasDao aliasDao = proxyFactory.create(RoomAliasDao.class, SessionFactory.class, hibernateBundle.getSessionFactory());
        MatrixTransactionDao transactionDao = proxyFactory
            .create(MatrixTransactionDao.class, SessionFactory.class, hibernateBundle.getSessionFactory());
        PuppetUserDao userDao = proxyFactory.create(PuppetUserDao.class, SessionFactory.class, hibernateBundle.getSessionFactory());

        checkMaster(configuration.getMatrix(), client, userDao);

        TransportPool pool = new TransportPool(xmppSessionConfiguration, new TransportConfiguration(configuration), client, service,
            aliasDao,
            userDao);
        environment.jersey().register(proxyFactory.create(ApplicationServiceEndpoint.class,
            new Class[] {TransportPool.class, MatrixTransactionDao.class},
            new Object[] {pool, transactionDao}));
        environment.lifecycle().manage(pool);
    }

    protected void checkMaster(MatrixConfig config, Client client, PuppetUserDao dao) {
        if (!dao.isMasterAvailable()) {
            try {
                MatrixClient mxClient = new MatrixClient(config.getHomeserver(), client, false, false);
                mxClient.setAccessToken(config.getAccessToken());
                RegisterRequest request = new RegisterRequest();
                request.setUsername(Id.localpart(config.getMasterUserId()));
                mxClient.account().register(request);
            } catch (MatrixException e) {
                LOGGER.warn("master user already registered", e);
            }
            PuppetUser masterUser = new PuppetUser();
            masterUser.setUserId(config.getMasterUserId());
            masterUser.setMaster(true);
            masterUser.setRoomId("pass-unique-id");
            dao.persist(masterUser);
        }
    }
}
