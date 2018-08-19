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
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.github.ma1uta.mjjb.dao.AppServerUserDao;
import io.github.ma1uta.mjjb.dao.RoomAliasDao;
import io.github.ma1uta.mjjb.matrix.ApplicationServiceEndpoint;
import io.github.ma1uta.mjjb.model.AppServerUser;
import io.github.ma1uta.mjjb.model.RoomAlias;
import io.github.ma1uta.mjjb.transaction.MatrixTransaction;
import io.github.ma1uta.mjjb.transaction.MatrixTransactionDao;
import io.github.ma1uta.mjjb.transport.PersistenceService;
import io.github.ma1uta.mjjb.transport.TransportConfiguration;
import io.github.ma1uta.mjjb.transport.TransportPool;
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
        MatrixTransaction.class, AppServerUser.class) {
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

        initObjectMapper(bootstrap);

        bootstrap.addBundle(hibernateBundle);
    }

    protected void initObjectMapper(Bootstrap<BridgeConfiguration> bootstrap) {
        ObjectMapper objectMapper = bootstrap.getObjectMapper();
        objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void run(BridgeConfiguration configuration, Environment environment) {
        Client client = new JerseyClientBuilder(environment).using(configuration.getHttpClient()).build("httpClient");

        XmppSessionConfiguration xmppSessionConfiguration = initXmppSessionConfiguration(configuration);

        PersistenceService service = initPersistenceService();

        checkMaster(configuration.getMatrix(), client, service);

        TransportPool pool = new TransportPool(xmppSessionConfiguration, new TransportConfiguration(configuration), client, service);

        environment.jersey().register(new ApplicationServiceEndpoint(pool, service));
        environment.lifecycle().manage(pool);
    }

    protected PersistenceService initPersistenceService() {
        UnitOfWorkAwareProxyFactory proxyFactory = new UnitOfWorkAwareProxyFactory(hibernateBundle);
        RoomAliasDao aliasDao = new RoomAliasDao(hibernateBundle.getSessionFactory());
        MatrixTransactionDao transactionDao = new MatrixTransactionDao(hibernateBundle.getSessionFactory());
        AppServerUserDao userDao = new AppServerUserDao(hibernateBundle.getSessionFactory());

        return new PersistenceService.Builder().aliasDao(aliasDao).txDao(transactionDao).userDao(userDao).proxy(proxyFactory).build();
    }

    protected XmppSessionConfiguration initXmppSessionConfiguration(BridgeConfiguration configuration) {
        XmppSessionConfiguration.Builder xmppSessionBuilder = XmppSessionConfiguration.builder();
        if (configuration.getXmpp().isConsole()) {
            xmppSessionBuilder.debugger(ConsoleDebugger.class);
        }
        return xmppSessionBuilder.build();
    }

    protected void checkMaster(MatrixConfig config, Client client, PersistenceService service) {
        service.tx(s -> {
            if (!s.getUserDao().exist(config.getMasterUserId())) {
                try {
                    MatrixClient mxClient = new MatrixClient(config.getHomeserver(), client, false, false);
                    mxClient.setAccessToken(config.getAccessToken());
                    RegisterRequest request = new RegisterRequest();
                    request.setUsername(Id.localpart(config.getMasterUserId()));
                    mxClient.account().register(request);
                } catch (MatrixException e) {
                    LOGGER.warn("master user already registered", e);
                }
                s.getUserDao().save(config.getMasterUserId());
            }
        });
    }
}
