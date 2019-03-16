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

import io.github.ma1uta.mjjb.xmpp.babbler.xml.XmppStreamDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import rocks.xmpp.core.stream.model.StreamElement;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.xml.bind.Unmarshaller;

/**
 * Decodes byte buffers to stream elements.
 * <br/>
 * This class should be added to Netty's channel pipeline.
 * The output of this decoder are implementations of {@link StreamElement}.
 *
 * @author Christian Schudt
 * @see NettyXmppEncoder
 */
final class NettyXmppDecoder extends ByteToMessageDecoder {

    private final BiConsumer<String, StreamElement> onRead;

    private final XmppStreamDecoder xmppStreamDecoder;

    private final Consumer<Throwable> onFailure;

    /**
     * Creates the decoder.
     *
     * @param onRead               The first parameter of this callback is the decoded XML element, the second one is
     *                             the unmarshalled element.
     * @param unmarshallerSupplier Supplies the unmarshaller, e.g. via a {@code ThreadLocal<Unmarshaller>}
     * @param onFailure            Called when an exception in the pipeline has occurred. If null, the exception is propagated
     *                             to next handler. If non-null this callback is called instead.
     */
    NettyXmppDecoder(final BiConsumer<String, StreamElement> onRead, final Supplier<Unmarshaller> unmarshallerSupplier,
                     final Consumer<Throwable> onFailure) {
        this.onRead = onRead;
        this.xmppStreamDecoder = new XmppStreamDecoder(unmarshallerSupplier);
        this.onFailure = onFailure;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf byteBuf, final List<Object> list) throws Exception {
        final ByteBuffer byteBuffer = byteBuf.nioBuffer();
        this.xmppStreamDecoder.decode(byteBuffer, (s, streamElement) -> {
            list.add(streamElement);
            if (onRead != null) {
                onRead.accept(s, streamElement);
            }
        });
        byteBuf.readerIndex(byteBuffer.position());
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (onFailure != null) {
            onFailure.accept(cause);
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }

    /**
     * Restarts the stream.
     */
    void restart() {
        this.xmppStreamDecoder.restart();
    }
}
