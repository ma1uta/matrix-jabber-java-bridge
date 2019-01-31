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
import io.github.ma1uta.mjjb.xmpp.dialback.ServerDialback;
import io.github.ma1uta.mjjb.xmpp.netty.XmppClientInitializer;
import io.github.ma1uta.mjjb.xmpp.netty.XmppServerInitializer;
import io.netty.channel.Channel;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.net.ChannelEncryption;
import rocks.xmpp.core.net.ConnectionConfiguration;
import rocks.xmpp.core.stanza.model.Stanza;
import rocks.xmpp.core.stream.model.StreamElement;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLContext;

/**
 * XMPP server with S2S support.
 */
public class XmppServer implements NetworkServer<XmppConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmppServer.class);

    private final Set<IncomingSession> initialIncomingSessions = new HashSet<>();
    private final Map<String, IncomingSession> establishedIncomingSessions = new HashMap<>();
    private final Map<String, OutgoingSession> establishedOutgoingSessions = new HashMap<>();
    private ServerDialback dialback;
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
     * @param session a new incoming session.
     */
    public void newIncomingSession(IncomingSession session) {
        LOGGER.debug("New incoming session.");
        getInitialIncomingSessions().add(session);
    }

    /**
     * Create new outgoing session.
     *
     * @param session a new outgoing session.
     */
    public void newOutgoingSession(OutgoingSession session) {
        String target = session.getJid().getDomain();
        LOGGER.debug("New outgoing session to {}.", target);
        getEstablishedOutgoingSessions().put(target, session);
        session.handshake();
    }

    public Set<IncomingSession> getInitialIncomingSessions() {
        return initialIncomingSessions;
    }

    public Map<String, IncomingSession> getEstablishedIncomingSessions() {
        return establishedIncomingSessions;
    }

    public Map<String, OutgoingSession> getEstablishedOutgoingSessions() {
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
            LOGGER.debug("Establish incoming session from {}", jid.getDomain());
        }
        incomingSession.setJid(jid);
        initialIncomingSessions.remove(incomingSession);
        establishedIncomingSessions.put(jid.getDomain(), incomingSession);
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
                LOGGER.error("Failed close connection to " + jid, e);
            }
        });
        getEstablishedOutgoingSessions().forEach((jid, s) -> {
            try {
                s.close();
            } catch (Exception e) {
                LOGGER.error("Failed close connection to " + jid, e);
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
        send(message.getTo(), message);
    }

    /**
     * Send outgoing message.
     *
     * @param to            remote address.
     * @param streamElement message.
     */
    public void send(Jid to, StreamElement streamElement) {
        sendInEventLoop(getSession(to), streamElement);
    }

    private void sendInEventLoop(OutgoingSession outgoingSession, StreamElement streamElement) {
        outgoingSession.getExecutor().execute(() -> outgoingSession.getConnection().send(streamElement));
    }

    private OutgoingSession connect(Jid jid) {
        NettyBuilder.createClient(jid.getDomain(), 5269, new XmppClientInitializer(this, jid), null);
        return getEstablishedOutgoingSessions().get(jid.getDomain());
    }

    private OutgoingSession getSession(Jid to) {
        OutgoingSession outgoingSession = getEstablishedOutgoingSessions().get(to.getDomain());
        if (outgoingSession == null) {
            outgoingSession = connect(to);
        }
        return outgoingSession;
    }

    @Override
    public void init(Jdbi jdbi, XmppConfig config, RouterFactory routerFactory) throws Exception {
        this.jdbi = jdbi;
        this.config = config;
        this.routerFactory = routerFactory;
        this.dialback = new ServerDialback(this);
        initSSL(config);
        initRouters();
    }

    private void initSSL(XmppConfig config) throws Exception {
        Cert cert = config.getSsl();
        if (cert != null) {
            this.sslContext = cert.createJavaContext();
        }
    }

    private void initRouters() {

    }

    /**
     * Provides ServerDialback mechanism.
     *
     * @return dialback service.
     */
    public ServerDialback dialback() {
        return dialback;
    }

    @Override
    public void run() {
        this.channel = NettyBuilder.createServer(config.getDomain(), config.getPort(), new XmppServerInitializer(this), null);
    }

    /**
     * Remove closed session.
     *
     * @param session session to remove.
     */
    public void remove(Session session) {
        if (session instanceof OutgoingSession) {
            getEstablishedOutgoingSessions().remove(session.getJid().getDomain());
        } else if (session instanceof IncomingSession) {
            if (session.getJid() != null) {
                getEstablishedIncomingSessions().remove(session.getJid().getDomain());
            } else {
                getInitialIncomingSessions().remove(session);
            }
        } else {
            LOGGER.error("Unknown session.");
        }
    }

    /**
     * Process incoming stanzas.
     *
     * @param stanza incoming stanzas.
     */
    public void process(Stanza stanza) {
        routerFactory.process(stanza);
    }
}
