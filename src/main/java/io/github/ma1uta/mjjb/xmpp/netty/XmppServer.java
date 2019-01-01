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
public class XmppServer {

    private final Set<IncomingSession> initialIncomingSessions = new HashSet<>();
    private final Map<Jid, IncomingSession> establishedIncomingSessions = new HashMap<>();
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
     * Create new session.
     *
     * @return new session.
     * @throws JAXBException when cannot create xml unmarshaller/marshaller.
     */
    public IncomingSession newSession() throws JAXBException {
        IncomingSession incomingSession = new IncomingSession(this);
        initialIncomingSessions.add(incomingSession);
        return incomingSession;
    }

    public Set<IncomingSession> getInitialIncomingSessions() {
        return initialIncomingSessions;
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
        initialIncomingSessions.remove(incomingSession);
        establishedIncomingSessions.put(jid, incomingSession);
    }

    public XmppConfig getConfig() {
        return config;
    }
}
