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
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.net.TcpBinding;
import rocks.xmpp.core.stream.StreamNegotiationResult;
import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.core.stream.server.ServerStreamFeaturesManager;

import java.util.Objects;
import java.util.concurrent.Executor;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * XMPP S2S outgoing session.
 */
public abstract class Session implements AutoCloseable {

    protected static final Logger LOGGER = LoggerFactory.getLogger(Session.class);
    protected static final Logger STANZA_LOGGER = LoggerFactory.getLogger(Loggers.STANZA_LOGGER);

    private Executor executor;
    private TcpBinding connection;
    private final Unmarshaller unmarshaller;
    private final Marshaller marshaller;
    private final ServerStreamFeaturesManager streamFeaturesManager = new ServerStreamFeaturesManager();
    private final XmppServer xmppServer;
    private String domain;

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
     * @throws XmppException when exception occured.
     */
    public boolean handleStream(Object streamElement) throws XmppException {
        if (streamElement instanceof StreamElement
            && getStreamFeaturesManager().handleElement((StreamElement) streamElement) == StreamNegotiationResult.RESTART) {
            return true;
        }
        return handle(streamElement);
    }

    protected abstract boolean handle(Object streamElement) throws XmppException;

    /**
     * Send message.
     *
     * @param streamElement message.
     */
    public abstract void send(StreamElement streamElement);

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
            close();
        } catch (Exception e) {
            LOGGER.error("Failed close xmpp connection", e);
        }
    }

    public ServerStreamFeaturesManager getStreamFeaturesManager() {
        return streamFeaturesManager;
    }

    public TcpBinding getConnection() {
        return connection;
    }

    public void setConnection(TcpBinding connection) {
        this.connection = connection;
    }

    public XmppServer getXmppServer() {
        return xmppServer;
    }

    @Override
    public void close() throws Exception {
        getXmppServer().remove(this);
        connection.close();
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o.getClass().equals(this.getClass()))) {
            return false;
        }
        Session session = (Session) o;
        return Objects.equals(connection, session.connection) && Objects.equals(domain, session.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connection, domain);
    }
}
