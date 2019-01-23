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

package io.github.ma1uta.mjjb.matrix;

import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.client.AppServiceClient;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.factory.jaxrs.AppJaxRsRequestFactory;
import io.github.ma1uta.matrix.client.model.account.RegisterRequest;
import io.github.ma1uta.matrix.event.RoomMember;
import io.github.ma1uta.matrix.event.RoomMessage;
import io.github.ma1uta.matrix.event.content.RoomMessageContent;
import io.github.ma1uta.matrix.event.message.Text;
import io.github.ma1uta.mjjb.Loggers;
import io.github.ma1uta.mjjb.NetworkServer;
import io.github.ma1uta.mjjb.RouterFactory;
import io.github.ma1uta.mjjb.config.Cert;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.db.UserDao;
import io.github.ma1uta.mjjb.matrix.converter.TextConverter;
import io.github.ma1uta.mjjb.matrix.netty.JerseyServerInitializer;
import io.github.ma1uta.mjjb.matrix.netty.NettyHttpContainer;
import io.github.ma1uta.mjjb.matrix.router.DirectInviteRouter;
import io.github.ma1uta.mjjb.matrix.router.MessageRouter;
import io.github.ma1uta.mjjb.netty.NettyBuilder;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stanza.model.Message;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * All Matrix endpoints.
 */
public class MatrixServer implements NetworkServer<MatrixConfig> {

    private MatrixApp matrixApp;
    private MatrixClient matrixClient;
    private SslContext sslContext;
    private MatrixConfig config;
    private Jdbi jdbi;
    private RouterFactory routerFactory;
    private Channel channel;

    @Override
    public void init(Jdbi jdbi, MatrixConfig config, RouterFactory routerFactory) {
        this.jdbi = jdbi;
        this.config = config;
        this.routerFactory = routerFactory;
        this.matrixClient = new AppServiceClient.Builder()
            .requestFactory(new AppJaxRsRequestFactory(config.getHomeserver()))
            .userId(Id.valueOf(config.getMasterUserId()))
            .accessToken(config.getAsToken())
            .build();

        initMasterBot();
        initRestAPI();
        initSSL();
        initRouters();
    }

    private void initMasterBot() {
        this.jdbi.useTransaction(h -> {
            UserDao userDao = h.attach(UserDao.class);
            Id masterId = Id.valueOf(getConfig().getMasterUserId());
            if (userDao.exist(masterId.getLocalpart()) == 0) {
                RegisterRequest request = new RegisterRequest();
                request.setUsername(masterId.getLocalpart());
                request.setInhibitLogin(false);
                getMatrixClient().account().register(request).join();
                userDao.create(masterId.getLocalpart());
            }
        });
    }

    private void initRestAPI() {
        MatrixAppResource appResource = new MatrixAppResource(jdbi, routerFactory, matrixClient);
        Set<Object> resources = new HashSet<>();
        resources.add(appResource);
        resources.add(new LegacyMatrixAppResource(appResource));
        resources.add(new SecurityContextFilter(config.getHsToken()));
        resources.add(new MatrixExceptionHandler());
        if (LoggerFactory.getLogger(Loggers.REQUEST_LOGGER).isDebugEnabled()) {
            resources.add(new LoggingFilter());
        }
        this.matrixApp = new MatrixApp(resources);
    }

    private void initSSL() {
        Cert cert = config.getSsl();
        if (cert != null) {
            try {
                sslContext = cert.createNettyContext();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void initRouters() {
        Map<Class<? extends RoomMessageContent>, BiFunction<Jid, RoomMessage<?>, Message>> messageConverters = new HashMap<>();
        messageConverters.put(Text.class, new TextConverter());
        MessageRouter router = new MessageRouter();
        router.setConverters(messageConverters);
        routerFactory.addMatrixRouter(RoomMessage.class, router);
        routerFactory.addMatrixRouter(RoomMember.class, new DirectInviteRouter());
    }

    @Override
    public void run() {
        URI uri = URI.create(getConfig().getUrl());

        NettyHttpContainer container = new NettyHttpContainer(matrixApp);
        JerseyServerInitializer initializer = new JerseyServerInitializer(uri, sslContext, container);
        this.channel = NettyBuilder.createServer(uri.getHost(), NettyBuilder.getPort(uri), initializer,
            f -> container.getApplicationHandler().onShutdown(container));
    }

    @Override
    public void close() throws Exception {
        channel.close().sync();
    }

    public MatrixConfig getConfig() {
        return config;
    }

    public MatrixClient getMatrixClient() {
        return matrixClient;
    }
}
