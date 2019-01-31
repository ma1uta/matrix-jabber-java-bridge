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

import io.github.ma1uta.mjjb.xmpp.dialback.ServerDialback;
import io.netty.channel.EventLoop;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.stream.model.StreamFeatures;
import rocks.xmpp.core.stream.model.StreamHeader;
import rocks.xmpp.core.tls.model.Proceed;
import rocks.xmpp.core.tls.model.StartTls;
import rocks.xmpp.extensions.compress.model.StreamCompression;
import rocks.xmpp.extensions.compress.model.feature.CompressionFeature;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

/**
 * XMPP S2S outgoing session.
 */
public class OutgoingSession extends Session {

    private EventLoop executor;
    private String compressMethod;

    public OutgoingSession(XmppServer xmppServer, Jid jid) throws JAXBException {
        super(xmppServer);
        setJid(jid);
    }

    public EventLoop getExecutor() {
        return executor;
    }

    public void setExecutor(EventLoop executor) {
        this.executor = executor;
    }

    @Override
    public boolean handleStream(Object streamElement) throws XmppException {
        if (super.handleStream(streamElement)) {
            return true;
        }
        if (streamElement instanceof Proceed) {
            try {
                getConnection().secureConnection();
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to secure connection", e);
                throw new XmppException(e);
            }
        }
        if (StreamCompression.COMPRESSED.equals(streamElement)) {
            try {
                getConnection().compressConnection(compressMethod, null);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to compress session.", e);
                throw new XmppException(e);
            }
        }
        if (streamElement instanceof StreamFeatures) {
            StreamFeatures features = (StreamFeatures) streamElement;
            for (Object feature : features.getFeatures()) {
                if (feature instanceof StartTls) {
                    getConnection().send(new StartTls());
                    break;
                }
                if (feature instanceof CompressionFeature) {
                    CompressionFeature compress = (CompressionFeature) feature;
                    if (!compress.getMethods().isEmpty()) {
                        compressMethod = compress.getMethods().get(0);
                    }
                    break;
                }
            }
        }
        return false;
    }

    /**
     * Handshake with remote server.
     */
    public void handshake() {
        getConnection().send(StreamHeader.initialServerToServer(Jid.of(getXmppServer().getConfig().getDomain()), getJid(), null, new QName(
            ServerDialback.NAMESPACE, ServerDialback.LOCALPART)));
    }
}
