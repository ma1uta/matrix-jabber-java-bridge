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

import io.github.ma1uta.mjjb.config.XmppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.net.ChannelEncryption;
import rocks.xmpp.core.net.ConnectionConfiguration;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.xml.bind.JAXBException;

/**
 * XMPP server with S2S support.
 */
public class XmppServer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmppServer.class);

    private final Set<IncomingSession> initialIncomingSessions = new HashSet<>();
    private final Map<Jid, IncomingSession> establishedIncomingSessions = new HashMap<>();
    private final Map<Jid, OutgoingSession> establishedOutgoingSessions = new HashMap<>();
    private final XmppConfig config;
    private final SSLContext sslContext;
    private final ConnectionConfiguration connectionConfig = new ConnectionConfiguration() {
        @Override
        public ChannelEncryption getChannelEncryption() {
            return sslContext != null ? ChannelEncryption.REQUIRED : ChannelEncryption.DISABLED;
        }

        @Override
        public SSLContext getSSLContext() {
            try {
                return sslContext != null ? sslContext : SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    };

    public XmppServer(XmppConfig config, SSLContext sslContext) {
        this.config = config;
        this.sslContext = sslContext;
    }

    /**
     * Create new incoming session.
     *
     * @return new incoming session.
     * @throws JAXBException when cannot create xml unmarshaller/marshaller.
     */
    public IncomingSession newIncomingSession() throws JAXBException {
        LOGGER.debug("New incoming session.");
        IncomingSession incomingSession = new IncomingSession(this);
        getInitialIncomingSessions().add(incomingSession);
        return incomingSession;
    }

    public OutgoingSession newOutgoingSession(Jid jid) throws JAXBException {
        LOGGER.debug("New outgoing session to {}.", jid.toString());
        OutgoingSession outgoingSession = new OutgoingSession(this, jid);
        getEstablishedOutgoingSessions().put(jid, outgoingSession);
        return outgoingSession;
    }

    public Set<IncomingSession> getInitialIncomingSessions() {
        return initialIncomingSessions;
    }

    public Map<Jid, IncomingSession> getEstablishedIncomingSessions() {
        return establishedIncomingSessions;
    }

    public Map<Jid, OutgoingSession> getEstablishedOutgoingSessions() {
        return establishedOutgoingSessions;
    }

    /**
     * Provide server connection configuration.
     *
     * @return server connection configuration.
     */
    public ConnectionConfiguration getConnectionConfiguration() {
        return connectionConfig;
    }

    public void establish(Jid jid, IncomingSession incomingSession) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Establish incoming session from {}", jid.toString());
        }
        incomingSession.setJid(jid);
        initialIncomingSessions.remove(incomingSession);
        establishedIncomingSessions.put(jid, incomingSession);
    }

    public XmppConfig getConfig() {
        return config;
    }

    @Override
    public void close() {
        getInitialIncomingSessions().forEach(s -> {
            try {
                s.close();
            } catch (Exception e) {
                LOGGER.error("Failed close connection.", e);
            }
        });
        getEstablishedIncomingSessions().forEach((jid, s) -> {
            try {
                s.close();
            } catch (Exception e) {
                LOGGER.error("Failed close connection to " + jid.toString(), e);
            }
        });
        getEstablishedOutgoingSessions().forEach((jid, s) -> {
            try {
                s.close();
            } catch (Exception e) {
                LOGGER.error("Failed close connection to " + jid.toString(), e);
            }
        });
    }
}
