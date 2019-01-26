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

import io.github.ma1uta.mjjb.xmpp.OutgoingSession;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.extensions.compress.server.CompressionNegotiator;
import rocks.xmpp.core.net.ChannelEncryption;
import rocks.xmpp.core.tls.server.StartTlsNegotiator;
import rocks.xmpp.nio.netty.net.NettyChannelConnection;

/**
 * XMPP server netty channel initializer.
 */
public class XmppClientInitializer extends ChannelInitializer<Channel> {

    private final XmppServer xmppServer;
    private final Jid jid;
    private NettyChannelConnection connection;

    public XmppClientInitializer(XmppServer xmppServer, Jid jid) {
        this.xmppServer = xmppServer;
        this.jid = jid;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        OutgoingSession outgoingSession = xmppServer.newOutgoingSession(jid);
        connection = new NettyChannelConnection(
            ch,
            outgoingSession::handleStream,
            outgoingSession::onRead,
            outgoingSession::getUnmarshaller,
            outgoingSession::onWrite,
            outgoingSession::getMarshaller,
            outgoingSession::onException,
            xmppServer.getConnectionConfiguration()
        );
        outgoingSession.setExecutor(ch.eventLoop());
        outgoingSession.setConnection(connection);
        if (xmppServer.getConnectionConfiguration().getChannelEncryption() == ChannelEncryption.REQUIRED) {
            outgoingSession.getStreamFeaturesManager().registerStreamFeatureNegotiator(new StartTlsNegotiator(connection));
        }
        outgoingSession.getStreamFeaturesManager().registerStreamFeatureNegotiator(new CompressionNegotiator(connection));
        outgoingSession.handshake();
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
