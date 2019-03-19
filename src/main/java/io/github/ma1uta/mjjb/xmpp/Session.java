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
import io.github.ma1uta.mjjb.xmpp.dialback.Result;
import io.github.ma1uta.mjjb.xmpp.dialback.Verify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.net.TcpBinding;
import rocks.xmpp.core.stanza.model.Stanza;
import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.core.stream.model.StreamErrorException;
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
    public abstract boolean handleStream(Object streamElement) throws XmppException;

    /**
     * Send message.
     *
     * @param streamElement message.
     */
    public abstract void send(StreamElement streamElement);

    /**
     * Session direction. Incoming or outgoing.
     *
     * @return session direction.
     */
    protected abstract String direction();

    /**
     * Read handler.
     *
     * @param xml     string representation of the stanza.
     * @param element stanza.
     */
    public void onRead(String xml, StreamElement element) {
        log(xml, element, " IN");
    }

    /**
     * Write handler.
     *
     * @param xml     string representation of the stanza.
     * @param element stanza.
     */
    public void onWrite(String xml, StreamElement element) {
        log(xml, element, "OUT");
    }

    protected void log(String xml, StreamElement element, String action) {
        if (STANZA_LOGGER.isDebugEnabled()) {
            String id = getConnection().getStreamId() != null ? getConnection().getStreamId() : "new";
            String domain = getDomain();
            if (domain == null && element instanceof Stanza) {
                domain = ((Stanza) element).getFrom().getDomain();
            }
            if (domain == null) {
                domain = "unknown";
            }
            STANZA_LOGGER.debug("[{} : {} : {}] {}: {}", direction(), domain, id, action, xml);
        }
    }

    /**
     * Validate stanza.
     * <br/>
     * Enforce the target equals current xmpp domain.
     *
     * @param streamElement stream element to validate.
     */
    protected boolean isStanzaInvalid(Object streamElement) {
        if (streamElement instanceof Stanza) {
            return isTargetDomainWrong(((Stanza) streamElement).getTo().getDomain());
        } else if (streamElement instanceof Result) {
            return isTargetDomainWrong(((Result) streamElement).getTo().getDomain());
        } else if (streamElement instanceof Verify) {
            return isTargetDomainWrong(((Verify) streamElement).getTo().getDomain());
        }
        return false;
    }

    private boolean isTargetDomainWrong(String domain) {
        if (!getXmppServer().getConfig().getDomain().equals(domain)) {
            STANZA_LOGGER.error(String.format("Wrong target: %s", domain));
            return true;
        }
        return false;
    }

    /**
     * Exception handler.
     *
     * @param throwable exception.
     */
    public void onException(Throwable throwable) {
        String id = getConnection().getStreamId() != null ? getConnection().getStreamId() : Integer.toString(hashCode());
        LOGGER.error(String.format("[%s : %s : %s] XMPP exception: ", direction(), getDomain(), id), throwable);
        if (throwable instanceof StreamElement) {
            getConnection().send((StreamElement) throwable);
        } else if (throwable instanceof StreamErrorException) {
            getConnection().send(((StreamErrorException) throwable).getError());
        }
        try {
            close();
        } catch (Exception e) {
            LOGGER.error(String.format("[%s : %s : %s] Failed close xmpp connection", direction(), getDomain(), id), e);
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
