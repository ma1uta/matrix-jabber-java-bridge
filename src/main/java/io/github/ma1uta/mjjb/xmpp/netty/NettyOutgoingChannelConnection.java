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

import io.netty.channel.Channel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import rocks.xmpp.core.net.ConnectionConfiguration;
import rocks.xmpp.core.stream.StreamHandler;
import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.nio.netty.net.NettyChannelConnection;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * Netty channel connection.
 */
public class NettyOutgoingChannelConnection extends NettyChannelConnection {

    public NettyOutgoingChannelConnection(Channel channel, StreamHandler streamHandler,
                                          BiConsumer<String, StreamElement> onRead,
                                          Supplier<Unmarshaller> unmarshallerSupplier,
                                          BiConsumer<String, StreamElement> onWrite,
                                          Supplier<Marshaller> marshallerSupplier,
                                          Consumer<Throwable> onException,
                                          ConnectionConfiguration connectionConfiguration) {
        super(channel, streamHandler, onRead, unmarshallerSupplier, onWrite, marshallerSupplier, onException, connectionConfiguration);
    }

    @Override
    public void secureConnection() throws Exception {
        final SSLContext sslContext = getConfiguration().getSSLContext();
        SslContext sslCtx = new JdkSslContext(sslContext, true, ClientAuth.NONE);
        final SslHandler handler = new SslHandler(sslCtx.newEngine(channel.alloc()), false);
        channel.pipeline().addFirst("SSL", handler);
    }
}
