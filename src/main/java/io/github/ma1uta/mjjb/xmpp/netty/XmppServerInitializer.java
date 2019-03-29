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

import io.github.ma1uta.mjjb.Loggers;
import io.github.ma1uta.mjjb.xmpp.IncomingSession;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import io.github.ma1uta.mjjb.xmpp.babbler.netty.NettyChannelConnection;
import io.github.ma1uta.mjjb.xmpp.dialback.DialbackNegotiator;
import io.github.ma1uta.mjjb.xmpp.sasl.SaslNegotiator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.compression.ZlibWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.core.extensions.compress.server.CompressionNegotiator;
import rocks.xmpp.core.net.ChannelEncryption;
import rocks.xmpp.core.tls.server.StartTlsNegotiator;

/**
 * XMPP server netty channel initializer.
 */
public class XmppServerInitializer extends XmppNettyInitializer<SocketChannel, IncomingSession> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Loggers.LOGGER);

    public XmppServerInitializer(XmppServer xmppServer) {
        super(xmppServer);
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        IncomingSession session = new IncomingSession(getServer());
        NettyChannelConnection connection = new NettyChannelConnection(
            ch,
            session::handleStream,
            session::onRead,
            session::getUnmarshaller,
            session::onWrite,
            session::getMarshaller,
            session::onException,
            getServer().getConnectionConfiguration()
        );
        session.setConnection(connection);
        session.setExecutor(ch.eventLoop());
        if (getServer().getConnectionConfiguration().getChannelEncryption() == ChannelEncryption.REQUIRED) {
            session.getStreamFeaturesManager().registerStreamFeatureNegotiator(new StartTlsNegotiator(connection));
        }
        session.getStreamFeaturesManager().registerStreamFeatureNegotiator(new CompressionNegotiator(connection,
            ZlibWrapper.GZIP.name().toLowerCase(), ZlibWrapper.ZLIB.name().toLowerCase()));
        session.getStreamFeaturesManager().registerStreamFeatureNegotiator(new DialbackNegotiator(connection, getServer()));
        session.getStreamFeaturesManager().registerStreamFeatureNegotiator(new SaslNegotiator(session));
        getServer().newIncomingSession(session);
        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                session.close();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                session.close();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error("Unable to open incoming session.", cause);
        super.exceptionCaught(ctx, cause);
    }
}
