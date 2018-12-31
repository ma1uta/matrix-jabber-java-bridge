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

package io.github.ma1uta.mjjb.matrix.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.net.URI;

/**
 * Jersey {@link ChannelInitializer}.
 * <p/>
 * Adds {@link HttpServerCodec}, {@link ChunkedWriteHandler} and {@link JerseyServerHandler} to the channels pipeline.
 */
public class JerseyServerInitializer extends ChannelInitializer<SocketChannel> {

    private final URI baseUri;
    private final SslContext sslCtx;
    private final NettyHttpContainer container;

    /**
     * Constructor.
     *
     * @param baseUri   base {@link URI} of the container (includes context path, if any).
     * @param sslCtx    SSL context.
     * @param container Netty container implementation.
     */
    public JerseyServerInitializer(URI baseUri, SslContext sslCtx, NettyHttpContainer container) {
        this.baseUri = baseUri;
        this.sslCtx = sslCtx;
        this.container = container;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }
        pipeline.addLast(new HttpServerCodec())
            .addLast(new ChunkedWriteHandler())
            .addLast(new JerseyServerHandler(baseUri, container));
    }
}
