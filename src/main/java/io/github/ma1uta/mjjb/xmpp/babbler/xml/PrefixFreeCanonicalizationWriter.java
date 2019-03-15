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

import io.github.ma1uta.mjjb.xmpp.dialback.ServerDialback;
import rocks.xmpp.core.stream.model.StreamHeader;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.soap.SOAPConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Writes XML in a prefix-free canonicalization form.
 * <blockquote>
 * <p><cite><a href="https://xmpp.org/rfcs/rfc6120.html#stanzas-extended">8.4.  Extended Content</a></cite></p>
 * <p>It is conventional in the XMPP community for implementations to not generate namespace prefixes for elements that are qualified
 * by extended namespaces (in the XML community, this convention is sometimes called "prefix-free canonicalization").
 * However, if an implementation generates such namespace prefixes then it MUST include the namespace declaration in the stanza itself
 * or a child element of the stanza, not in the stream header (see Section 4.8.4).</p>
 * </blockquote>
 * See also <a href="http://stackoverflow.com/questions/5720501/jaxb-marshalling-xmpp-stanzas">here</a> for implementation idea.
 *
 * @author Christian Schudt
 * @author Anatoly Sablin
 */
final class PrefixFreeCanonicalizationWriter implements XMLStreamWriter, NamespaceContext {

    private static final Collection<String> PREFIXED_NAMESPACES = Arrays.asList(
        SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE,
        SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE
    );

    private final XMLStreamWriter xsw;

    private final boolean writeStreamNamespace;

    private final Map<String, String> urisByPrefix = new HashMap<>();

    PrefixFreeCanonicalizationWriter(final XMLStreamWriter xsw, final boolean writeStreamNamespace) {
        this.xsw = xsw;
        this.writeStreamNamespace = writeStreamNamespace;
    }

    @Override
    public void writeStartElement(final String localName) throws XMLStreamException {
        xsw.writeStartElement(localName);
    }

    @Override
    public void writeStartElement(final String namespaceURI, final String localName) throws XMLStreamException {
        xsw.writeStartElement(namespaceURI, localName);
    }

    @Override
    public void writeStartElement(final String prefix, final String localName, final String namespaceURI) throws XMLStreamException {
        writeElement(prefix, localName, namespaceURI, xsw::writeStartElement);
    }

    @Override
    public void writeEmptyElement(final String namespaceURI, final String localName) throws XMLStreamException {
        xsw.writeEmptyElement(namespaceURI, localName);
    }

    @Override
    public void writeEmptyElement(final String prefix, final String localName, final String namespaceURI) throws XMLStreamException {
        writeElement(prefix, localName, namespaceURI, xsw::writeEmptyElement);
    }

    @Override
    public void writeEmptyElement(final String localName) throws XMLStreamException {
        xsw.writeEmptyElement(localName);
    }

    private void writeElement(final String prefix, final String localName, final String namespaceURI, ElementWriter writeElement) throws
        XMLStreamException {
        if (shouldWriteNamespacePrefix(namespaceURI) || isDialback(prefix, namespaceURI)) {
            String preparedPrefix = prefix;
            if (ServerDialback.NAMESPACE.equals(namespaceURI) && ("result".equals(localName) || "verify".equals(localName))) {
                preparedPrefix = "db";
            }
            writeElement.writeElement(preparedPrefix, localName, namespaceURI);
        } else {
            // If the writer wants to write a prefix, instead don't write it.
            final String namespaceUriInScope = getNamespaceContext().getNamespaceURI(XMLConstants.DEFAULT_NS_PREFIX);
            writeElement.writeElement(XMLConstants.DEFAULT_NS_PREFIX, localName, namespaceURI);
            if ((namespaceUriInScope == null || !Objects.equals(namespaceUriInScope, namespaceURI)) && !XMLConstants.DEFAULT_NS_PREFIX
                .equals(namespaceURI)) {
                xsw.writeDefaultNamespace(namespaceURI);
            }
        }
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        xsw.writeEndElement();
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        xsw.writeEndDocument();
    }

    @Override
    public void close() throws XMLStreamException {
        xsw.close();
    }

    @Override
    public void flush() throws XMLStreamException {
        xsw.flush();
    }

    @Override
    public void writeAttribute(final String localName, final String value) throws XMLStreamException {
        xsw.writeAttribute(localName, value);
    }

    @Override
    public void writeAttribute(final String prefix, final String namespaceURI, final String localName, String value) throws
        XMLStreamException {
        // If an attribute has an extra namespace, we need to write that namespace to the element.
        // Do it only once for each element.
        if (!XMLConstants.XML_NS_URI.equals(namespaceURI) && getNamespaceContext().getPrefix(namespaceURI) == null) {
            xsw.writeNamespace(prefix, namespaceURI);
        }
        if (XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(namespaceURI) && "type".equals(localName)) {
            final String[] split = value.split(":", 2);
            if (split.length > 1) {
                final String typePrefix = split[0];
                // Check if there's a known namespace URI for the prefix.
                final String namespaceUri = getNamespaceURI(typePrefix);
                // Check the prefix for the URI.
                // If it's the default namespace, we don't need to write it.
                // Otherwise (if the prefix is unknown) write it.
                final String pref = xsw.getPrefix(namespaceUri);
                if (!XMLConstants.DEFAULT_NS_PREFIX.equals(pref) && !XMLConstants.NULL_NS_URI
                    .equals(namespaceUri) && namespaceUri != null) {
                    xsw.writeNamespace(typePrefix, namespaceUri);
                } else {
                    // Only write the namespace, not the prefix, if the prefix' namespace is already the default namespace.
                    value = split[1];
                }
            }
        }
        xsw.writeAttribute(prefix, namespaceURI, localName, value);
    }

    @Override
    public void writeAttribute(final String namespaceURI, final String localName, final String value) throws XMLStreamException {
        xsw.writeAttribute(namespaceURI, localName, value);
    }

    @Override
    public void writeNamespace(final String prefix, final String namespaceURI) throws XMLStreamException {
        // do not write a namespace with a prefix, except it's allowed.
        if (shouldWriteNamespace(namespaceURI) || isDialback(prefix, namespaceURI)) {
            xsw.writeNamespace(prefix, namespaceURI);
        } else {
            urisByPrefix.put(prefix, namespaceURI);
        }
    }

    @Override
    public void writeDefaultNamespace(final String namespaceURI) throws XMLStreamException {
        xsw.writeDefaultNamespace(namespaceURI);
    }

    @Override
    public void writeComment(final String data) throws XMLStreamException {
        xsw.writeComment(data);
    }

    @Override
    public void writeProcessingInstruction(final String target) throws XMLStreamException {
        xsw.writeProcessingInstruction(target);
    }

    @Override
    public void writeProcessingInstruction(final String target, final String data) throws XMLStreamException {
        xsw.writeProcessingInstruction(target, data);
    }

    @Override
    public void writeCData(final String data) throws XMLStreamException {
        xsw.writeCData(data);
    }

    @Override
    public void writeDTD(final String dtd) throws XMLStreamException {
        xsw.writeDTD(dtd);
    }

    @Override
    public void writeEntityRef(final String name) throws XMLStreamException {
        xsw.writeEntityRef(name);
    }

    @Override
    public void writeStartDocument() throws XMLStreamException {
        xsw.writeStartDocument();
    }

    @Override
    public void writeStartDocument(String version) throws XMLStreamException {
        xsw.writeStartDocument(version);
    }

    @Override
    public void writeStartDocument(final String encoding, final String version) throws XMLStreamException {
        xsw.writeStartDocument(encoding, version);
    }

    @Override
    public void writeCharacters(final String text) throws XMLStreamException {
        xsw.writeCharacters(text);
    }

    @Override
    public void writeCharacters(final char[] text, final int start, final int len) throws XMLStreamException {
        xsw.writeCharacters(text, start, len);
    }

    @Override
    public String getNamespaceURI(final String prefix) {
        String uri = getNamespaceContext().getNamespaceURI(prefix);
        if (uri == null || XMLConstants.NULL_NS_URI.equals(uri)) {
            uri = urisByPrefix.get(prefix);
            if (uri == null) {
                return XMLConstants.NULL_NS_URI;
            }
        }
        return uri;
    }

    @Override
    public String getPrefix(final String uri) {
        return getNamespaceContext().getPrefix(uri);
    }

    @Override
    public Iterator getPrefixes(final String namespaceURI) {
        return getNamespaceContext().getPrefixes(namespaceURI);
    }

    @Override
    public void setPrefix(final String prefix, final String uri) throws XMLStreamException {
        xsw.setPrefix(prefix, uri);
    }

    @Override
    public void setDefaultNamespace(final String uri) throws XMLStreamException {
        xsw.setDefaultNamespace(uri);
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        return xsw.getNamespaceContext();
    }

    @Override
    public void setNamespaceContext(final NamespaceContext context) throws XMLStreamException {
        xsw.setNamespaceContext(context);
    }

    @Override
    public Object getProperty(final String name) throws IllegalArgumentException {
        return xsw.getProperty(name);
    }

    private boolean shouldWriteNamespace(String namespaceURI) {
        return PREFIXED_NAMESPACES.stream().anyMatch(namespace -> getNamespaceContext().getPrefix(namespace) != null
            || namespaceURI.equals(namespace))
            || (StreamHeader.STREAM_NAMESPACE.equals(namespaceURI) && writeStreamNamespace);
    }

    private boolean shouldWriteNamespacePrefix(String namespaceURI) {
        return shouldWriteNamespace(namespaceURI)
            || StreamHeader.STREAM_NAMESPACE.equals(namespaceURI)
            || ServerDialback.NAMESPACE.equals(namespaceURI);
    }

    private boolean isDialback(String prefix, String namespaceURI) {
        return ServerDialback.NAMESPACE.equals(namespaceURI) && ServerDialback.PREFIX.equals(prefix);
    }

    private interface ElementWriter {
        void writeElement(String prefix, String localName, String namespaceURI) throws XMLStreamException;
    }
}
