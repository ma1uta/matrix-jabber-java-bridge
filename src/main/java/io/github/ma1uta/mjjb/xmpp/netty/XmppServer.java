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

import rocks.xmpp.core.net.ChannelEncryption;
import rocks.xmpp.core.net.ConnectionConfiguration;

import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.xml.bind.JAXBException;

/**
 * XMPP server with S2S support.
 */
public class XmppServer {

    private final Set<Session> sessions = new HashSet<>();
    private final ConnectionConfiguration connectionConfig = new ConnectionConfiguration() {
        @Override
        public ChannelEncryption getChannelEncryption() {
            return ChannelEncryption.REQUIRED;
        }

        @Override
        public SSLContext getSSLContext() {
            try {
                return SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    };

    /**
     * Create new session.
     *
     * @return new session.
     * @throws JAXBException when cannot create xml unmarshaller/marshaller.
     */
    public Session newSession() throws JAXBException {
        Session session = new Session();
        sessions.add(session);
        return session;
    }

    /**
     * Provide server connection configuration.
     *
     * @return server connection configuration.
     */
    public ConnectionConfiguration getConnectionConfiguration() {
        return connectionConfig;
    }
}
