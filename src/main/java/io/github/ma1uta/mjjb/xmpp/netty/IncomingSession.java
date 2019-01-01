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

import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stream.StreamNegotiationResult;
import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.core.stream.model.StreamFeatures;
import rocks.xmpp.core.stream.model.StreamHeader;
import rocks.xmpp.core.stream.server.ServerStreamFeaturesManager;
import rocks.xmpp.nio.netty.net.NettyChannelConnection;

import java.util.Locale;
import java.util.UUID;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * XMPP S2S session.
 */
public class IncomingSession implements AutoCloseable {

    private NettyChannelConnection connection;
    private final Unmarshaller unmarshaller;
    private final Marshaller marshaller;
    private final ServerStreamFeaturesManager streamFeaturesManager = new ServerStreamFeaturesManager();
    private final XmppServer xmppServer;

    public IncomingSession(XmppServer xmppServer) throws JAXBException {
        this.xmppServer = xmppServer;
        this.unmarshaller = ServerConfiguration.JAXB_CONTEXT.createUnmarshaller();
        this.marshaller = ServerConfiguration.JAXB_CONTEXT.createMarshaller();
    }

    public Unmarshaller getUnmarshaller() {
        return unmarshaller;
    }

    public Marshaller getMarshaller() {
        return marshaller;
    }

    /**
     * Handle stream (open, close, restart).
     *
     * @param streamElement stream stanza.
     * @return {@code true} to restart stream, else {@code false}.
     */
    public boolean handleStream(Object streamElement) {
        if (streamElement instanceof StreamHeader) {
            StreamHeader streamHeader = (StreamHeader) streamElement;
            getXmppServer().establish(streamHeader.getFrom(), this);
            // send stream header response
            getConnection().send(StreamHeader.responseServerToServer(
                Jid.of(getXmppServer().getConfig().getDomain()),
                streamHeader.getFrom(),
                UUID.randomUUID().toString(),
                Locale.getDefault()
            ));
            // send supported features.
            getConnection().send(new StreamFeatures(getStreamFeaturesManager().getStreamFeatures()));
        }
        if (streamElement instanceof StreamElement) {
            StreamNegotiationResult result = getStreamFeaturesManager().handleElement((StreamElement) streamElement);
            if (result == StreamNegotiationResult.RESTART) {
                return true;
            }
        }
        return false;
    }

    /**
     * Read handler.
     *
     * @param xml     string representation of the stanza.
     * @param element stanza.
     */
    public void onRead(String xml, StreamElement element) {

    }

    /**
     * Write handler.
     *
     * @param xml     string representation of the stanza.
     * @param element stanza.
     */
    public void onWrite(String xml, StreamElement element) {

    }

    /**
     * Exception handler.
     *
     * @param throwable exception.
     */
    public void onException(Throwable throwable) {
        if (throwable instanceof StreamElement) {
            getConnection().send((StreamElement) throwable);
        }
        try {
            getConnection().close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ServerStreamFeaturesManager getStreamFeaturesManager() {
        return streamFeaturesManager;
    }

    public NettyChannelConnection getConnection() {
        return connection;
    }

    public void setConnection(NettyChannelConnection connection) {
        this.connection = connection;
    }

    public XmppServer getXmppServer() {
        return xmppServer;
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
