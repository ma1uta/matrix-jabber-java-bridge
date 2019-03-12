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

package io.github.ma1uta.mjjb.xmpp.babbler.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.net.AbstractConnection;
import rocks.xmpp.core.net.ConnectionConfiguration;
import rocks.xmpp.core.net.TcpBinding;
import rocks.xmpp.core.session.model.SessionOpen;
import rocks.xmpp.core.stream.StreamHandler;
import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.core.stream.model.StreamHeader;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * A NIO connection based on Netty.
 *
 * @author Christian Schudt
 */
public class NettyChannelConnection extends AbstractConnection implements TcpBinding {

    protected final Channel channel;

    private final NettyXmppDecoder decoder;

    private final BiConsumer<String, StreamElement> onRead;

    protected SessionOpen sessionOpen;

    private final StreamHandler streamHandler;

    private final Consumer<Throwable> onException;

    public NettyChannelConnection(final Channel channel,
                                  final StreamHandler streamHandler,
                                  final BiConsumer<String, StreamElement> onRead,
                                  final Supplier<Unmarshaller> unmarshallerSupplier,
                                  final BiConsumer<String, StreamElement> onWrite,
                                  final Supplier<Marshaller> marshallerSupplier,
                                  final Consumer<Throwable> onException,
                                  final ConnectionConfiguration connectionConfiguration) {
        super(connectionConfiguration);
        this.channel = channel;
        this.onRead = onRead;
        this.streamHandler = streamHandler;
        this.onException = onException;
        this.decoder = new NettyXmppDecoder(this::onRead, unmarshallerSupplier, onException);
        channel.pipeline().addLast(decoder, new NettyXmppEncoder(onWrite, marshallerSupplier, onException));
    }

    private static <T> CompletableFuture<T> completableFutureFromNettyFuture(final Future<T> future) {
        final CompletableFuture<T> completableFuture = new CompletableFuture<>();
        future.addListener(f -> {
            if (f.isSuccess()) {
                completableFuture.complete(future.getNow());
            } else {
                completableFuture.completeExceptionally(future.cause());
            }
        });
        return completableFuture;
    }

    @Override
    public final CompletionStage<Void> write(final StreamElement streamElement) {
        return write(streamElement, channel::write);
    }

    private CompletionStage<Void> write(final StreamElement streamElement, final Function<StreamElement, ChannelFuture> writeFunction) {
        if (!isClosed() || streamElement == StreamHeader.CLOSING_STREAM_TAG) {
            return completableFutureFromNettyFuture(writeFunction.apply(streamElement));
        } else {
            final CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            completableFuture.completeExceptionally(new IllegalStateException("Connection closed"));
            return completableFuture;
        }
    }

    private void onRead(final String xml, final StreamElement streamElement) {
        if (onRead != null) {
            onRead.accept(xml, streamElement);
        }
        if (streamElement instanceof SessionOpen) {
            openedByPeer((SessionOpen) streamElement);
        } else if (streamElement == StreamHeader.CLOSING_STREAM_TAG) {
            closedByPeer();
        }
        try {
            if (streamHandler.handleElement(streamElement)) {
                restartStream();
            }
        } catch (XmppException e) {
            onException.accept(e);
        }
    }

    @Override
    public final InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    @Override
    public final CompletionStage<Void> open(final SessionOpen sessionOpen) {
        this.sessionOpen = sessionOpen;
        return send(sessionOpen);
    }

    @Override
    public final CompletionStage<Void> send(final StreamElement streamElement) {
        return write(streamElement, channel::writeAndFlush);
    }

    @Override
    public final void flush() {
        channel.flush();
    }

    @Override
    public void secureConnection() throws Exception {
        final SSLContext sslContext = getConfiguration().getSSLContext();
        SslContext sslCtx = new JdkSslContext(sslContext, false, ClientAuth.NONE);
        final SslHandler handler = new SslHandler(sslCtx.newEngine(channel.alloc()), true);
        channel.pipeline().addFirst("SSL", handler);
    }

    /**
     * Compresses the connection.
     *
     * @param method    The compression method. Supported methods are: "zlib", "deflate" and "gzip".
     * @param onSuccess Invoked after the compression method has been chosen, but before compression is applied.
     * @throws IllegalArgumentException If the compression method is unknown.
     */
    @Override
    public final void compressConnection(final String method, final Runnable onSuccess) {
        final ZlibWrapper zlibWrapper;
        switch (method) {
            case "zlib":
                zlibWrapper = ZlibWrapper.ZLIB;
                break;
            case "deflate":
                zlibWrapper = ZlibWrapper.NONE;
                break;
            case "gzip":
                zlibWrapper = ZlibWrapper.GZIP;
                break;
            default:
                throw new IllegalArgumentException("Compression method '" + method + "' not supported");
        }
        if (onSuccess != null) {
            onSuccess.run();
        }
        final ChannelHandler channelHandler = channel.pipeline().get("SSL");
        if (channelHandler != null) {
            channel.pipeline().addAfter("SSL", "decompressor", new JdkZlibDecoder(zlibWrapper));
            channel.pipeline().addAfter("SSL", "compressor", new JdkZlibEncoder(zlibWrapper));
        } else {
            channel.pipeline().addFirst("decompressor", new JdkZlibDecoder(zlibWrapper));
            channel.pipeline().addFirst("compressor", new JdkZlibEncoder(zlibWrapper));
        }
    }

    @Override
    public final boolean isSecure() {
        return channel.pipeline().toMap().containsKey("SSL");
    }

    @Override
    protected void restartStream() {
        decoder.restart();
    }

    @Override
    public final CompletionStage<Void> closeFuture() {
        return completableFutureFromNettyFuture(channel.closeFuture());
    }

    @Override
    protected final CompletionStage<Void> closeStream() {
        return send(StreamHeader.CLOSING_STREAM_TAG);
    }

    @Override
    protected CompletionStage<Void> closeConnection() {
        return completableFutureFromNettyFuture(channel.close());
    }

    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder("TCP NIO connection at ").append(channel.remoteAddress());
        final String streamId = getStreamId();
        if (streamId != null) {
            sb.append(" (").append(streamId).append(')');
        }
        return sb.toString();
    }
}
