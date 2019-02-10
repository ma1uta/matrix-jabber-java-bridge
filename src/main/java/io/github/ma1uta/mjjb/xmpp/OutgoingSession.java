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

import io.github.ma1uta.mjjb.netty.NettyBuilder;
import io.github.ma1uta.mjjb.xmpp.dialback.ServerDialback;
import io.github.ma1uta.mjjb.xmpp.netty.XmppClientInitializer;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.core.stream.model.StreamFeatures;
import rocks.xmpp.core.stream.model.StreamHeader;
import rocks.xmpp.core.tls.model.Proceed;
import rocks.xmpp.core.tls.model.StartTls;
import rocks.xmpp.extensions.compress.model.StreamCompression;
import rocks.xmpp.extensions.compress.model.feature.CompressionFeature;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

/**
 * XMPP S2S outgoing session.
 */
public class OutgoingSession extends Session {

    private String compressMethod;
    private ServerDialback.DialbackResult dialback;
    private AtomicBoolean initialized = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<StreamElement> queue;

    public OutgoingSession(XmppServer xmppServer, String domain, boolean dialback,
                           ConcurrentLinkedQueue<StreamElement> queue) throws JAXBException {
        super(xmppServer);
        this.dialback = dialback ? null : ServerDialback.DialbackResult.DISABLED;
        this.queue = queue;
        setDomain(domain);
    }

    /**
     * Dialback status.
     *
     * @return dialback status.
     */
    public ServerDialback.DialbackResult dialback() {
        return dialback;
    }

    /**
     * Set the new dialback status.
     *
     * @param result dialback status.
     */
    public void dialback(ServerDialback.DialbackResult result) {
        this.dialback = result;
    }

    public boolean isDialbackEnabled() {
        return dialback != ServerDialback.DialbackResult.DISABLED;
    }

    @Override
    public boolean handle(Object streamElement) throws XmppException {
        if (streamElement instanceof StreamHeader) {
            return false;
        }
        if (streamElement instanceof Proceed) {
            try {
                getConnection().secureConnection();
                handshake();
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to secure connection", e);
                throw new XmppException(e);
            }
        }
        if (StreamCompression.COMPRESSED.equals(streamElement)) {
            try {
                getConnection().compressConnection(compressMethod, null);
                handshake();
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to compress session.", e);
                throw new XmppException(e);
            }
        }
        if (streamElement instanceof StreamFeatures) {
            StreamFeatures features = (StreamFeatures) streamElement;
            for (Object feature : features.getFeatures()) {
                /*if (feature instanceof Dialback) {
                    return false;
                }*/
                if (feature instanceof StartTls) {
                    sendDirect(new StartTls());
                    return false;
                }
                if (feature instanceof CompressionFeature) {
                    CompressionFeature compress = (CompressionFeature) feature;
                    if (!compress.getMethods().isEmpty()) {
                        compressMethod = compress.getMethods().get(0);
                    }
                    if (compressMethod != null) {
                        sendDirect(new StreamCompression.Compress(compressMethod));
                        return false;
                    }
                }
            }
        }

        switch (getXmppServer().dialback().negotiateOutgoing(this, streamElement)) {
            case IN_PROCESS:
            case SUCCESS:
            case RESTART:
            case FAILED:
            case IGNORED:
            default:
                // nothing to do
        }
        // send all queued stanzas.
        initialized.compareAndSet(false, true);
        tryToSend();
        return false;
    }

    /**
     * Handshake with remote server.
     */
    public void handshake() {
        StreamHeader header = isDialbackEnabled()
            ? StreamHeader.initialServerToServer(
            Jid.ofDomain(getXmppServer().getConfig().getDomain()),
            Jid.ofDomain(getDomain()),
            Locale.ENGLISH,
            new QName(ServerDialback.NAMESPACE, "", ServerDialback.LOCALPART))

            : StreamHeader.initialServerToServer(
            Jid.ofDomain(getXmppServer().getConfig().getDomain()),
            Jid.ofDomain(getDomain()),
            Locale.ENGLISH
        );
        getExecutor().execute(() -> {
            try {
                getConnection().open(header);
            } catch (Exception e) {
                LOGGER.error("Unable to open connection.", e);
                throw e;
            }
        });
    }

    @Override
    public void send(StreamElement streamElement) {
        queue.offer(streamElement);
        tryToSend();
    }

    protected void sendDirect(StreamElement streamElement) {
        getExecutor().execute(() -> {
            try {
                getConnection().send(streamElement);
            } catch (Exception e) {
                LOGGER.error("Failed to send message.", e);
            }
        });
    }

    /**
     * Try to send all messages.
     */
    public synchronized void tryToSend() {
        if (initialized.get() && !queue.isEmpty()) {
            getExecutor().execute(() -> {
                while (!queue.isEmpty()) {
                    try {
                        getConnection().send(queue.poll());
                    } catch (Exception e) {
                        LOGGER.error("Failed to send message.", e);
                    }
                }
            });
        }
        if (!initialized.get()) {
            connect();
        }
    }

    /**
     * Connect to the target domain.
     */
    public void connect() {
        getXmppServer().getSrvNameResolver().resolve(getDomain(),
            (hostname, port) -> NettyBuilder.createClient(hostname, port, new XmppClientInitializer(getXmppServer(), this), null));
    }
}
