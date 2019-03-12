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

import io.github.ma1uta.mjjb.xmpp.babbler.xml.XmppStreamEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import rocks.xmpp.core.stream.model.StreamElement;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.xml.bind.Marshaller;
import javax.xml.stream.XMLOutputFactory;

/**
 * Encodes stream elements to byte buffers.
 * <br/>
 * This class should be added to Netty's channel pipeline.
 *
 * @author Christian Schudt
 */
final class NettyXmppEncoder extends MessageToByteEncoder<StreamElement> {

    private final BiConsumer<String, StreamElement> onWrite;

    private final XmppStreamEncoder xmppStreamEncoder;

    private final Consumer<Throwable> onFailure;

    /**
     * Constructor.
     *
     * @param onWrite            The first parameter of this callback is the encoded XML element, the second one is the marshalled element.
     * @param marshallerSupplier Supplies the marshaller, e.g. via a {@code ThreadLocal<Marshaller>}
     * @param onFailure          Called when an exception in the pipeline has occurred. If null, the exception is propagated
     *                           to next handler. If non-null this callback is called instead.
     */
    NettyXmppEncoder(final BiConsumer<String, StreamElement> onWrite, final Supplier<Marshaller> marshallerSupplier,
                     final Consumer<Throwable> onFailure) {
        this.onWrite = onWrite;
        this.xmppStreamEncoder = new XmppStreamEncoder(XMLOutputFactory.newFactory(), marshallerSupplier, Function.identity());
        this.onFailure = onFailure;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx, final StreamElement streamElement, final ByteBuf byteBuf) throws
        Exception {
        try (OutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
            xmppStreamEncoder.encode(streamElement, outputStream);
            if (onWrite != null) {
                onWrite.accept(byteBuf.toString(StandardCharsets.UTF_8), streamElement);
            }
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (onFailure != null) {
            onFailure.accept(cause);
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }
}
