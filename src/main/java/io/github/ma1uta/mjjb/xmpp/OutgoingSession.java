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
import io.github.ma1uta.mjjb.xmpp.dialback.Dialback;
import io.github.ma1uta.mjjb.xmpp.dialback.ServerDialback;
import io.github.ma1uta.mjjb.xmpp.netty.XmppClientInitializer;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.sasl.model.Abort;
import rocks.xmpp.core.sasl.model.Auth;
import rocks.xmpp.core.sasl.model.Failure;
import rocks.xmpp.core.sasl.model.Mechanisms;
import rocks.xmpp.core.sasl.model.Success;
import rocks.xmpp.core.stream.StreamNegotiationException;
import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.core.stream.model.StreamError;
import rocks.xmpp.core.stream.model.StreamErrorException;
import rocks.xmpp.core.stream.model.StreamFeatures;
import rocks.xmpp.core.stream.model.StreamHeader;
import rocks.xmpp.core.stream.model.errors.Condition;
import rocks.xmpp.core.tls.model.Proceed;
import rocks.xmpp.core.tls.model.StartTls;
import rocks.xmpp.extensions.compress.model.StreamCompression;
import rocks.xmpp.extensions.compress.model.feature.CompressionFeature;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

/**
 * XMPP S2S outgoing session.
 */
public class OutgoingSession extends Session {

    /**
     * 'EXTERNAL' sasl mechanism.
     */
    public static final String EXTERNAL = "EXTERNAL";

    private String compressMethod;
    private ServerDialback.State dialback;
    private Collection<String> supported;
    private AtomicBoolean initialized = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<StreamElement> queue = new ConcurrentLinkedQueue<>();

    public OutgoingSession(XmppServer xmppServer, String domain, boolean dialback) throws JAXBException {
        super(xmppServer);
        this.dialback = dialback ? null : ServerDialback.State.DISABLED;
        setDomain(domain);
    }

    /**
     * Dialback status.
     *
     * @return dialback status.
     */
    public ServerDialback.State dialback() {
        return dialback;
    }

    /**
     * Set the new dialback status.
     *
     * @param result dialback status.
     */
    public void dialback(ServerDialback.State result) {
        this.dialback = result;
    }

    public boolean isDialbackEnabled() {
        return dialback != ServerDialback.State.DISABLED;
    }

    @Override
    public boolean handleStream(Object streamElement) throws XmppException {
        if (isStanzaInvalid(streamElement)) {
            throw new StreamErrorException(new StreamError(Condition.IMPROPER_ADDRESSING));
        }
        if (streamElement instanceof StreamHeader) {
            StreamHeader header = (StreamHeader) streamElement;
            List<QName> namespaces = header.getAdditionalNamespaces();
            for (QName namespace : namespaces) {
                if (dialback() == null
                    && ServerDialback.NAMESPACE.equals(namespace.getNamespaceURI())
                    && ServerDialback.PREFIX.equals(namespace.getPrefix())) {
                    dialback(ServerDialback.State.SUPPORT);
                }
            }
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
            if (dialback() == null) {
                for (Object feature : features.getFeatures()) {
                    if (feature instanceof Dialback) {
                        dialback(ServerDialback.State.SUPPORT);
                    }
                }
            }
            for (Object feature : features.getFeatures()) {
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
                if (feature instanceof Mechanisms) {
                    Mechanisms mechanisms = (Mechanisms) feature;
                    supported = mechanisms.getMechanisms();
                    if (supported.contains(EXTERNAL)) {
                        sendDirect(new Auth(EXTERNAL, "=".getBytes(StandardCharsets.US_ASCII)));
                    }
                    return false;
                }
            }
        }
        if (streamElement instanceof Success) {
            initialized.set(true);
        }
        if (streamElement instanceof Abort || streamElement instanceof Failure) {
            throw new StreamNegotiationException(new Failure(Failure.Condition.NOT_AUTHORIZED));
        }

        if (!initialized.get()) {
            switch (getXmppServer().dialback().negotiateOutgoing(this, streamElement)) {
                case IN_PROCESS:
                case FAILED:
                    return false;
                case SUCCESS:
                case IGNORED:
                default:
                    // nothing to do
            }
            // send all queued stanzas.
            if (ServerDialback.State.DISABLED.equals(dialback()) || ServerDialback.State.TRUSTED.equals(dialback())) {
                initialized.compareAndSet(false, true);
            }
        }
        if (initialized.get()) {
            tryToSend();
        }
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
            new QName(ServerDialback.NAMESPACE, "", ServerDialback.PREFIX))

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

    @Override
    protected String direction() {
        return "outcome";
    }

    /**
     * Send message bypassing outgoing queue.
     *
     * @param streamElement message to send.
     */
    public void sendDirect(StreamElement streamElement) {
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
