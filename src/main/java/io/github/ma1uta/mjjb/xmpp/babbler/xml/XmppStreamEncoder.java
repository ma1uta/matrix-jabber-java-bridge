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

package io.github.ma1uta.mjjb.xmpp.babbler.xml;

import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.core.stream.model.StreamError;
import rocks.xmpp.core.stream.model.StreamErrorException;
import rocks.xmpp.core.stream.model.StreamHeader;
import rocks.xmpp.core.stream.model.errors.Condition;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Encodes XMPP elements to binary data.
 * This class is capable to encode elements to either an {@link OutputStream} or to a {@link ByteBuffer}.
 * <br>
 * Encoding is thread-safe, as long as the supplied {@link Marshaller} is not shared by another thread, e.g.
 * if a {@linkplain ThreadLocal thread-local} {@link Marshaller} is supplied.
 *
 * @author Christian Schudt
 */
public final class XmppStreamEncoder {

    private final XMLOutputFactory outputFactory;

    private final Supplier<Marshaller> marshaller;

    private final Function<StreamElement, StreamElement> stanzaMapper;

    private String contentNamespace;

    /**
     * Creates the XMPP encoder.
     * <br>
     * Because {@link Marshaller} is not thread-safe, it is recommended to pass a {@code ThreadLocal<Marshaller>} to this constructor,
     * which ensures thread-safety during marshalling.
     *
     * @param outputFactory The XML output factory.
     * @param marshaller    Supplies the marshaller which will convert objects to XML.
     * @param stanzaMapper  Maps stanzas to a specific type, which is required for correct marshalling.
     */
    public XmppStreamEncoder(final XMLOutputFactory outputFactory, final Supplier<Marshaller> marshaller,
                             final Function<StreamElement, StreamElement> stanzaMapper) {
        this.marshaller = marshaller;
        this.stanzaMapper = stanzaMapper;
        this.outputFactory = outputFactory;
    }

    /**
     * Encodes an XMPP element to an {@link OutputStream}.
     *
     * @param streamElement The stream element.
     * @param outputStream  The output stream to write to.
     * @throws StreamErrorException If the element could not be marshalled.
     */
    public void encode(StreamElement streamElement, final OutputStream outputStream) throws StreamErrorException {
        try {
            if (streamElement instanceof StreamHeader) {
                contentNamespace = ((StreamHeader) streamElement).getContentNamespace();
                final XMLStreamWriter writer = outputFactory.createXMLStreamWriter(outputStream, StandardCharsets.UTF_8.name());
                ((StreamHeader) streamElement).writeTo(writer);
                return;
            } else if (streamElement == StreamHeader.CLOSING_STREAM_TAG) {
                outputStream.write(StreamHeader.CLOSING_STREAM_TAG.toString().getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                return;
            }
            streamElement = stanzaMapper.apply(streamElement);

            final XMLStreamWriter streamWriter = new PrefixFreeCanonicalizationWriter(
                outputFactory.createXMLStreamWriter(outputStream, StandardCharsets.UTF_8.name()), false);
            streamWriter.setDefaultNamespace(contentNamespace);
            final Marshaller m = marshaller.get();
            m.setProperty(Marshaller.JAXB_FRAGMENT, true);
            m.marshal(streamElement, streamWriter);
            streamWriter.flush();
        } catch (XMLStreamException | JAXBException | IOException e) {
            throw new StreamErrorException(new StreamError(Condition.INTERNAL_SERVER_ERROR), e);
        }
    }
}
