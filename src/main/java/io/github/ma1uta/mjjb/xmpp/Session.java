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

package io.github.ma1uta.mjjb.xmpp;

import io.github.ma1uta.mjjb.Loggers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.net.Connection;
import rocks.xmpp.core.stream.StreamNegotiationResult;
import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.core.stream.server.ServerStreamFeaturesManager;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * XMPP S2S outgoing session.
 */
public class Session implements AutoCloseable {

    protected static final Logger LOGGER = LoggerFactory.getLogger(Session.class);
    protected static final Logger STANZA_LOGGER = LoggerFactory.getLogger(Loggers.STANZA_LOGGER);

    private Connection connection;
    private final Unmarshaller unmarshaller;
    private final Marshaller marshaller;
    private final ServerStreamFeaturesManager streamFeaturesManager = new ServerStreamFeaturesManager();
    private final XmppServer xmppServer;
    private Jid jid;

    public Session(XmppServer xmppServer) throws JAXBException {
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
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(streamElement.toString());
        }
        if (streamElement instanceof StreamElement) {
            StreamNegotiationResult result = getStreamFeaturesManager().handleElement((StreamElement) streamElement);
            return result == StreamNegotiationResult.RESTART;
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
        STANZA_LOGGER.debug("<< " + xml);
    }

    /**
     * Write handler.
     *
     * @param xml     string representation of the stanza.
     * @param element stanza.
     */
    public void onWrite(String xml, StreamElement element) {
        STANZA_LOGGER.debug(">> " + xml);
    }

    /**
     * Exception handler.
     *
     * @param throwable exception.
     */
    public void onException(Throwable throwable) {
        LOGGER.error("XMPP exception: ", throwable);
        if (throwable instanceof StreamElement) {
            getConnection().send((StreamElement) throwable);
        }
        try {
            getConnection().close();
        } catch (Exception e) {
            LOGGER.error("Failed close xmpp connection", e);
        }
    }

    public ServerStreamFeaturesManager getStreamFeaturesManager() {
        return streamFeaturesManager;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public XmppServer getXmppServer() {
        return xmppServer;
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }

    public Jid getJid() {
        return jid;
    }

    public void setJid(Jid jid) {
        this.jid = jid;
    }
}
