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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.netty.connector.internal.NettyInputStream;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.internal.ContainerUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import javax.ws.rs.core.SecurityContext;

/**
 * {@link io.netty.channel.ChannelInboundHandler} which servers as a bridge
 * between Netty and Jersey.
 */
public class JerseyServerHandler extends ChannelInboundHandlerAdapter {

    private final URI baseUri;
    private final LinkedBlockingDeque<InputStream> isList = new LinkedBlockingDeque<>();
    private final NettyHttpContainer container;

    /**
     * Constructor.
     *
     * @param baseUri   base {@link URI} of the container (includes context path, if any).
     * @param container Netty container implementation.
     */
    public JerseyServerHandler(URI baseUri, NettyHttpContainer container) {
        this.baseUri = baseUri;
        this.container = container;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {

        if (msg instanceof HttpRequest) {
            final HttpRequest req = (HttpRequest) msg;

            if (HttpUtil.is100ContinueExpected(req)) {
                ctx.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
            }

            isList.clear(); // clearing the content - possible leftover from previous request processing.
            final ContainerRequest requestContext = createContainerRequest(ctx, req);

            requestContext.setWriter(new NettyResponseWriter(ctx, req, container));

            // deserialization may be slow so do it in another thread.
            container.getExecutorService().execute(() -> container.getApplicationHandler().handle(requestContext));
        }

        if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;

            ByteBuf content = httpContent.content();

            if (content.isReadable()) {
                isList.add(new ByteBufInputStream(content));
            }

            if (msg instanceof LastHttpContent) {
                isList.add(NettyInputStream.END_OF_INPUT);
            }
        }
    }

    /**
     * Create Jersey {@link ContainerRequest} based on Netty {@link HttpRequest}.
     *
     * @param ctx Netty channel context.
     * @param req Netty Http request.
     * @return created Jersey Container Request.
     */
    private ContainerRequest createContainerRequest(ChannelHandlerContext ctx, HttpRequest req) {

        String requestUrl = req.uri().startsWith("/") ? req.uri().substring(1) : req.uri();
        String path = baseUri.toString();
        if (!path.endsWith("/")) {
            path += "/";
        }
        URI requestUri = URI.create(path + ContainerUtils.encodeUnsafeCharacters(requestUrl));

        ContainerRequest requestContext = new ContainerRequest(
            URI.create(path), requestUri, req.method().name(), getSecurityContext(),
            new PropertiesDelegate() {

                private final Map<String, Object> properties = new HashMap<>();

                @Override
                public Object getProperty(String name) {
                    return properties.get(name);
                }

                @Override
                public Collection<String> getPropertyNames() {
                    return properties.keySet();
                }

                @Override
                public void setProperty(String name, Object object) {
                    properties.put(name, object);
                }

                @Override
                public void removeProperty(String name) {
                    properties.remove(name);
                }
            });

        // request entity handling.
        if ((req.headers().contains(HttpHeaderNames.CONTENT_LENGTH) && HttpUtil.getContentLength(req) > 0)
            || HttpUtil.isTransferEncodingChunked(req)) {

            ctx.channel().closeFuture().addListener(future -> isList.add(NettyInputStream.END_OF_INPUT_ERROR));

            requestContext.setEntityStream(new NettyInputStream(isList));
        } else {
            requestContext.setEntityStream(new InputStream() {
                @Override
                public int read() throws IOException {
                    return -1;
                }
            });
        }

        // copying headers from netty request to jersey container request context.
        for (String name : req.headers().names()) {
            requestContext.headers(name, req.headers().getAll(name));
        }

        return requestContext;
    }

    private SecurityContext getSecurityContext() {
        return new SecurityContext() {

            @Override
            public boolean isUserInRole(final String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public Principal getUserPrincipal() {
                return null;
            }

            @Override
            public String getAuthenticationScheme() {
                return null;
            }
        };
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
