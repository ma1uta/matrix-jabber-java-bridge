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

import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLContext;

/**
 * XMPP server with S2S support.
 */
public class XmppServer implements NetworkServer<XmppConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmppServer.class);

    private final Map<InetSocketAddress, Set<IncomingSession>> incoming = new ConcurrentHashMap<>();
    private final Map<String, Set<OutgoingSession>> outgoing = new ConcurrentHashMap<>();
    private final Map<String, Set<OutgoingSession>> withoutDialback = new HashMap<>();
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
        getIncoming().computeIfAbsent(session.getConnection().getRemoteAddress(), k -> new HashSet<>()).add(session);
    }

    /**
     * Create new outgoing session.
     *
     * @param session a new outgoing session.
     */
    public void newOutgoingSession(OutgoingSession session) {
        LOGGER.debug("New outgoing session.");
        if (session.isDialbackEnabled()) {
            getOutgoing().computeIfAbsent(session.getDomain(), k -> new HashSet<>()).add(session);
        } else {
            getWithoutDialback().computeIfAbsent(session.getDomain(), k -> new HashSet<>()).add(session);
        }
        session.handshake();
    }

    public Map<InetSocketAddress, Set<IncomingSession>> getIncoming() {
        return incoming;
    }

    public Map<String, Set<OutgoingSession>> getOutgoing() {
        return outgoing;
    }

    public Map<String, Set<OutgoingSession>> getWithoutDialback() {
        return withoutDialback;
    }

    /**
     * Provide server connection configuration.
     *
     * @return server connection configuration.
     */
    public ConnectionConfiguration getConnectionConfiguration() {
        return connectionConfig;
    }

    public XmppConfig getConfig() {
        return config;
    }

    @Override
    public void close() throws Exception {
        List<Session> sessionsToRemove = new ArrayList<>();
        getIncoming().values().forEach(sessionsToRemove::addAll);
        getOutgoing().values().forEach(sessionsToRemove::addAll);
        for (Session session : sessionsToRemove) {
            try {
                session.close();
            } catch (Exception e) {
                LOGGER.error("Failed to close session.", e);
            }
        }
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
        send(to, streamElement, (domain, element) -> connect(domain, true, element));
    }

    protected void send(Jid to, StreamElement streamElement, BiConsumer<String, StreamElement> connect) {
        String domain = to.getDomain();
        OutgoingSession session = getSession(domain);
        if (session == null) {
            connect.accept(domain, streamElement);
        } else {
            session.send(streamElement);
        }
    }

    /**
     * Send outgoing message without dialback.
     *
     * @param to            remote address.
     * @param streamElement message.
     */
    public void sendWithoutDialback(Jid to, StreamElement streamElement) {
        send(to, streamElement, (domain, element) -> connect(domain, false, element));
    }

    private void connect(String domain, boolean dialback, StreamElement streamElement) {
        ConcurrentLinkedQueue<StreamElement> queue = new ConcurrentLinkedQueue<>();
        queue.offer(streamElement);
        NettyBuilder.createClient(domain, 5269, new XmppClientInitializer(this, domain, dialback, queue), null);
    }

    private OutgoingSession getSession(String domain) {
        Set<OutgoingSession> sessions = getOutgoing().get(domain);
        return sessions != null && !sessions.isEmpty() ? sessions.iterator().next() : null;
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
            OutgoingSession outgoingSession = (OutgoingSession) session;
            Map<String, Set<OutgoingSession>> sessionMap = outgoingSession.isDialbackEnabled()
                ? getOutgoing()
                : getWithoutDialback();
            Set<OutgoingSession> sessions = sessionMap.get(session.getDomain());
            if (sessions != null) {
                sessions.remove(session);
            }
        } else if (session instanceof IncomingSession) {
            Set<IncomingSession> sessions = getIncoming().get(session.getConnection().getRemoteAddress());
            if (sessions != null) {
                sessions.remove(session);
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
