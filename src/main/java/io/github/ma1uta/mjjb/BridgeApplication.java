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
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.sslreload.SslReloadBundle;
import io.github.ma1uta.jeon.exception.MatrixException;
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.model.account.RegisterRequest;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.dao.AppServerUserDao;
import io.github.ma1uta.mjjb.matrix.ApplicationServiceEndpoint;
import io.github.ma1uta.mjjb.transport.TransportConfiguration;
import io.github.ma1uta.mjjb.transport.TransportPool;
import org.jdbi.v3.core.Jdbi;
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

        bootstrap.addBundle(new MigrationsBundle<BridgeConfiguration>() {
            @Override
            public PooledDataSourceFactory getDataSourceFactory(BridgeConfiguration configuration) {
                return configuration.getDatabase();
            }
        });
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
        Jdbi jdbi = new JdbiFactory().build(environment, configuration.getDatabase(), "postgresql");

        checkMaster(configuration.getMatrix(), client, jdbi);

        TransportPool pool = new TransportPool(initXmpp(configuration), new TransportConfiguration(configuration), client, jdbi);

        environment.jersey().register(new ApplicationServiceEndpoint(pool, jdbi));
        environment.lifecycle().manage(pool);
    }

    protected XmppSessionConfiguration initXmpp(BridgeConfiguration configuration) {
        XmppSessionConfiguration.Builder xmppSessionBuilder = XmppSessionConfiguration.builder();
        if (configuration.getXmpp().isConsole()) {
            xmppSessionBuilder.debugger(ConsoleDebugger.class);
        }
        return xmppSessionBuilder.build();
    }

    protected void checkMaster(MatrixConfig config, Client client, Jdbi jdbi) {
        jdbi.useHandle(handle -> {
            AppServerUserDao dao = handle.attach(AppServerUserDao.class);
            if (dao.count(config.getMasterUserId()) == 0) {
                MatrixClient mxClient = new MatrixClient(config.getHomeserver(), client, false, false);
                mxClient.setAccessToken(config.getAccessToken());
                String nick = Id.localpart(config.getMasterUserId());
                try {
                    RegisterRequest request = new RegisterRequest();
                    request.setUsername(nick);
                    request.setInitialDeviceDisplayName(nick);
                    mxClient.account().register(request);
                } catch (MatrixException e) {
                    LOGGER.warn("master user already registered", e);
                }
                dao.save(config.getMasterUserId());
            }
        });
    }
}
