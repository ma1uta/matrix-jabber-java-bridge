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

package io.github.ma1uta.mjjb.xmpp.netty;

import io.github.ma1uta.mjjb.xmpp.Session;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import rocks.xmpp.core.extensions.compress.server.CompressionNegotiator;
import rocks.xmpp.core.net.ChannelEncryption;
import rocks.xmpp.core.tls.server.StartTlsNegotiator;
import rocks.xmpp.nio.netty.net.NettyChannelConnection;

/**
 * Netty initializer.
 *
 * @param <C> socket type, client or server.
 * @param <S> session type, incoming or outgoing.
 */
public abstract class XmppNettyInitializer<C extends Channel, S extends Session> extends ChannelInitializer<C> {

    private final XmppServer server;
    private NettyChannelConnection connection;

    protected XmppNettyInitializer(XmppServer server) {
        this.server = server;
    }

    public XmppServer getServer() {
        return server;
    }

    public NettyChannelConnection getConnection() {
        return connection;
    }

    protected abstract S createSession() throws Exception;

    protected abstract void notifyServer(S session);

    @Override
    protected void initChannel(C ch) throws Exception {
        S session = createSession();
        initConnection(ch, session);
        notifyServer(session);
    }

    protected void initConnection(C channel, S session) {
        connection = new NettyChannelConnection(
            channel,
            session::handleStream,
            session::onRead,
            session::getUnmarshaller,
            session::onWrite,
            session::getMarshaller,
            session::onException,
            getServer().getConnectionConfiguration()
        );
        session.setConnection(connection);
        session.setExecutor(channel.eventLoop());
        if (getServer().getConnectionConfiguration().getChannelEncryption() == ChannelEncryption.REQUIRED) {
            session.getStreamFeaturesManager().registerStreamFeatureNegotiator(new StartTlsNegotiator(getConnection()));
        }
        session.getStreamFeaturesManager().registerStreamFeatureNegotiator(new CompressionNegotiator(getConnection()));
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        connection.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        connection.close();
    }

}
