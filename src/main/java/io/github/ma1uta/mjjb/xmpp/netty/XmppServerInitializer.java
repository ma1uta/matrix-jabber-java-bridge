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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import rocks.xmpp.core.extensions.compress.server.CompressionNegotiator;
import rocks.xmpp.core.tls.server.StartTlsNegotiator;
import rocks.xmpp.nio.netty.net.NettyChannelConnection;

/**
 * XMPP server netty channel initializer.
 */
public class XmppServerInitializer extends ChannelInitializer<SocketChannel> {

    private final XmppServer xmppServer;
    private NettyChannelConnection connection;

    public XmppServerInitializer(XmppServer xmppServer) {
        this.xmppServer = xmppServer;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        Session session = xmppServer.newSession();
        connection = new NettyChannelConnection(
            ch,
            session::handleStream,
            session::onRead,
            session::getUnmarshaller,
            session::onWrite,
            session::getMarshaller,
            session::onException,
            xmppServer.getConnectionConfiguration()
        );
        session.setConnection(connection);
        session.getStreamFeaturesManager().registerStreamFeatureNegotiator(new StartTlsNegotiator(connection));
        session.getStreamFeaturesManager().registerStreamFeatureNegotiator(new CompressionNegotiator(connection));
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
