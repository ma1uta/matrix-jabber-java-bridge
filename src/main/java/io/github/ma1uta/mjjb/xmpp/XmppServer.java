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

import io.github.ma1uta.mjjb.NetworkServer;
import io.github.ma1uta.mjjb.RouterFactory;
import io.github.ma1uta.mjjb.config.Cert;
import io.github.ma1uta.mjjb.config.XmppConfig;
import io.github.ma1uta.mjjb.netty.NettyBuilder;
import io.github.ma1uta.mjjb.xmpp.netty.XmppServerInitializer;
import io.netty.channel.Channel;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.net.ChannelEncryption;
import rocks.xmpp.core.net.ConnectionConfiguration;
import rocks.xmpp.core.stanza.model.Stanza;

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
public class XmppServer implements NetworkServer<XmppConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmppServer.class);

    private final Set<IncomingSession> initialIncomingSessions = new HashSet<>();
    private final Map<Jid, IncomingSession> establishedIncomingSessions = new HashMap<>();
    private final Map<Jid, OutgoingSession> establishedOutgoingSessions = new HashMap<>();
    private Jdbi jdbi;
    private XmppConfig config;
    private RouterFactory routerFactory;
    private SSLContext sslContext;
    private Channel channel;
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

    /**
     * Create new outgoing session.
     *
     * @param jid remote target.
     * @return outgoint session.
     * @throws JAXBException when cannote create xml unmarshaller/marshaller.
     */
    public OutgoingSession newOutgoingSession(Jid jid) throws JAXBException {
        LOGGER.debug("New outgoing session to {}.", jid.toString());
        OutgoingSession outgoingSession = new OutgoingSession(this, jid);
        getEstablishedOutgoingSessions().put(jid, outgoingSession);
        outgoingSession.handshake();
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

    /**
     * Establish session.
     *
     * @param jid             remote JID.
     * @param incomingSession session.
     */
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
    public void close() throws Exception {
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
        this.channel.close().sync();
    }

    /**
     * Send outgoing message.
     *
     * @param message outgoing stanza.
     */
    public void send(Stanza message) {
        OutgoingSession outgoingSession = getEstablishedOutgoingSessions().get(message.getTo());
        if (outgoingSession != null) {
            send0(outgoingSession, message);
        } else {
            try {
                send0(newOutgoingSession(message.getTo()), message);
            } catch (JAXBException e) {
                LOGGER.error("Failed create JAXB marshaller/unmarshaller.", e);
            }
        }
    }

    private void send0(OutgoingSession outgoingSession, Stanza message) {
        outgoingSession.getConnection().send(message);
    }

    @Override
    public void init(Jdbi jdbi, XmppConfig config, RouterFactory routerFactory) {
        this.jdbi = jdbi;
        this.config = config;
        this.routerFactory = routerFactory;
        initSSL(config);
        initRouters();
    }

    private void initSSL(XmppConfig config) {
        Cert cert = config.getSsl();
        if (cert != null) {
            try {
                this.sslContext = cert.createJavaContext();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void initRouters() {

    }

    @Override
    public void run() {
        this.channel = NettyBuilder.createServer(config.getDomain(), config.getPort(), new XmppServerInitializer(this), null);
    }
}
