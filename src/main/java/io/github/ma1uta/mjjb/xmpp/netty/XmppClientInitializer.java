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
import io.github.ma1uta.mjjb.xmpp.OutgoingSession;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XMPP server netty channel initializer.
 */
public class XmppClientInitializer extends XmppNettyInitializer<Channel, OutgoingSession> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Loggers.LOGGER);

    private final OutgoingSession session;

    public XmppClientInitializer(XmppServer xmppServer, OutgoingSession session) {
        super(xmppServer);
        this.session = session;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        NettyOutgoingChannelConnection connection = new NettyOutgoingChannelConnection(
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
        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                session.handshake();
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                session.close();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                session.close();
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof Throwable) {
                    LoggerFactory.getLogger(Loggers.LOGGER).error("Exception.", (Throwable) evt);
                }
                super.userEventTriggered(ctx, evt);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error("Unable to open outgoing session.", cause);
        super.exceptionCaught(ctx, cause);
    }
}
