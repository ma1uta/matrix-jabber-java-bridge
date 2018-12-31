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

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import org.glassfish.jersey.netty.connector.internal.JerseyChunkedInput;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty implementation of {@link ContainerResponseWriter}.
 */
class NettyResponseWriter implements ContainerResponseWriter {

    private static final Logger LOGGER = Logger.getLogger(NettyResponseWriter.class.getName());

    private static final ChannelFutureListener FLUSH_FUTURE = future -> future.channel().flush();

    private final ChannelHandlerContext ctx;
    private final HttpRequest req;
    private final NettyHttpContainer container;

    private volatile ScheduledFuture<?> suspendTimeoutFuture;
    private volatile Runnable suspendTimeoutHandler;

    private boolean responseWritten = false;

    NettyResponseWriter(ChannelHandlerContext ctx, HttpRequest req, NettyHttpContainer container) {
        this.ctx = ctx;
        this.req = req;
        this.container = container;
    }

    @Override
    public synchronized OutputStream writeResponseStatusAndHeaders(long contentLength, ContainerResponse responseContext)
        throws ContainerException {

        if (responseWritten) {
            LOGGER.log(Level.FINE, "Response already written.");
            return null;
        }

        responseWritten = true;

        String reasonPhrase = responseContext.getStatusInfo().getReasonPhrase();
        int statusCode = responseContext.getStatus();

        HttpResponseStatus status = reasonPhrase == null
            ? HttpResponseStatus.valueOf(statusCode)
            : new HttpResponseStatus(statusCode, reasonPhrase);

        DefaultHttpResponse response;
        if (contentLength == 0) {
            response = new DefaultFullHttpResponse(req.protocolVersion(), status);
        } else {
            response = new DefaultHttpResponse(req.protocolVersion(), status);
        }

        for (final Map.Entry<String, List<String>> e : responseContext.getStringHeaders().entrySet()) {
            response.headers().add(e.getKey(), e.getValue());
        }

        if (contentLength == -1) {
            HttpUtil.setTransferEncodingChunked(response, true);
        } else {
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
        }

        if (HttpUtil.isKeepAlive(req)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        ctx.writeAndFlush(response);

        if (req.method() != HttpMethod.HEAD && (contentLength > 0 || contentLength == -1)) {

            JerseyChunkedInput jerseyChunkedInput = new JerseyChunkedInput(ctx.channel());

            if (HttpUtil.isTransferEncodingChunked(response)) {
                ctx.write(new HttpChunkedInput(jerseyChunkedInput)).addListener(FLUSH_FUTURE);
            } else {
                ctx.write(new HttpChunkedInput(jerseyChunkedInput)).addListener(FLUSH_FUTURE);
            }
            return jerseyChunkedInput;

        } else {
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            return null;
        }
    }

    @Override
    public boolean suspend(long timeOut, TimeUnit timeUnit, final TimeoutHandler
        timeoutHandler) {

        suspendTimeoutHandler = () -> timeoutHandler.onTimeout(NettyResponseWriter.this);

        if (timeOut <= 0) {
            return true;
        }

        suspendTimeoutFuture =
            container.getScheduledExecutorService().schedule(suspendTimeoutHandler, timeOut, timeUnit);

        return true;
    }

    @Override
    public void setSuspendTimeout(long timeOut, TimeUnit timeUnit) throws IllegalStateException {

        // suspend(0, .., ..) was called, so suspendTimeoutFuture is null.
        if (suspendTimeoutFuture != null) {
            suspendTimeoutFuture.cancel(true);
        }

        if (timeOut <= 0) {
            return;
        }

        suspendTimeoutFuture =
            container.getScheduledExecutorService().schedule(suspendTimeoutHandler, timeOut, timeUnit);
    }

    @Override
    public void commit() {
        ctx.flush();
    }

    @Override
    public void failure(Throwable error) {
        ctx.writeAndFlush(new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR))
            .addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public boolean enableResponseBuffering() {
        return true;
    }
}
