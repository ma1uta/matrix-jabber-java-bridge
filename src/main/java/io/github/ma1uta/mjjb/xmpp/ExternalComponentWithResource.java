/*
 * Copyright sablintolya@gmail.com
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

import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.net.client.ClientConnectionConfiguration;
import rocks.xmpp.core.net.client.SocketConnectionConfiguration;
import rocks.xmpp.core.session.XmppSession;
import rocks.xmpp.core.session.XmppSessionConfiguration;
import rocks.xmpp.core.session.model.SessionOpen;
import rocks.xmpp.core.stanza.model.IQ;
import rocks.xmpp.core.stanza.model.Message;
import rocks.xmpp.core.stanza.model.Presence;
import rocks.xmpp.core.stanza.model.Stanza;
import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.extensions.component.accept.ExternalComponent;
import rocks.xmpp.extensions.component.accept.model.ComponentIQ;
import rocks.xmpp.extensions.component.accept.model.ComponentMessage;
import rocks.xmpp.extensions.component.accept.model.ComponentPresence;
import rocks.xmpp.extensions.component.accept.model.Handshake;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Modification of the original ExternalComponent with feature to specify jid's resource.
 */
public final class ExternalComponentWithResource extends XmppSession {

    private static final int WAIT_TIMEOUT = 50;

    private static final Logger LOGGER = Logger.getLogger(ExternalComponent.class.getName());

    private volatile CompletableFuture<SessionOpen> streamOpened;

    private volatile CompletableFuture<Void> handshakeReceived;

    private final String sharedSecret;

    private volatile Jid connectedResource;

    private ExternalComponentWithResource(String componentName, String sharedSecret, XmppSessionConfiguration configuration,
                                          ClientConnectionConfiguration connectionConfiguration) {
        super(componentName, configuration, connectionConfiguration);
        this.sharedSecret = sharedSecret;
    }

    /**
     * Creates a new external component using a default configuration. Any registered {@link #addCreationListener(Consumer)} creation
     * listeners are triggered.
     *
     * @param componentName The component name.
     * @param sharedSecret  The shared secret (password).
     * @param hostname      The hostname to connect to.
     * @param port          The port to connect to.
     * @return The external component.
     */
    public static ExternalComponentWithResource create(String componentName, String sharedSecret, String hostname, int port) {
        return create(componentName, sharedSecret, XmppSessionConfiguration.getDefault(), hostname, port);
    }

    /**
     * Creates a new external component. Any registered {@link #addCreationListener(Consumer) creation listeners} are triggered.
     *
     * @param componentName The component name.
     * @param sharedSecret  The shared secret (password).
     * @param configuration The configuration.
     * @param hostname      The hostname to connect to.
     * @param port          The port to connect to.
     * @return The external component.
     */
    public static ExternalComponentWithResource create(String componentName, String sharedSecret, XmppSessionConfiguration configuration,
                                                       String hostname, int port) {
        return create(componentName, sharedSecret, configuration,
            SocketConnectionConfiguration.builder().hostname(hostname).port(port).build());
    }

    /**
     * Creates a new external component using a default configuration. Any registered {@link XmppSession#addCreationListener(Consumer)}
     * creation listeners are triggered.
     *
     * @param componentName            The component name.
     * @param sharedSecret             The shared secret (password).
     * @param xmppSessionConfiguration The XMPP configuration.
     * @param connectionConfiguration  The connection configuration.
     * @return The external component.
     */
    public static ExternalComponentWithResource create(String componentName, String sharedSecret,
                                                       XmppSessionConfiguration xmppSessionConfiguration,
                                                       ClientConnectionConfiguration connectionConfiguration) {
        ExternalComponentWithResource component = new ExternalComponentWithResource(componentName, sharedSecret, xmppSessionConfiguration,
            connectionConfiguration);
        notifyCreationListeners(component);
        return component;
    }

    @Override
    public void connect(Jid from) throws XmppException {
        Status previousStatus = preConnect();

        try {
            if (!checkConnected()) {
                // Don't call listeners from within synchronized blocks to avoid possible deadlocks.

                updateStatus(Status.CONNECTING);
                synchronized (this) {
                    streamOpened = new CompletableFuture<>();
                    // Double-checked locking: Recheck connected status. In a multi-threaded environment multiple threads could
                    // have passed the first check.
                    if (!checkConnected()) {
                        // Reset
                        exception = null;

                        tryConnect(from, "jabber:component:accept", "1.0");
                        LOGGER.fine("Negotiating stream, waiting until handshake is ready to be negotiated.");
                        SessionOpen sessionOpen = streamOpened
                            .get(configuration.getDefaultResponseTimeout().toMillis(), TimeUnit.MILLISECONDS);

                        // Check if the server returned a stream error, e.g. conflict.
                        throwAsXmppExceptionIfNotNull(exception);

                        if (sessionOpen != null && sessionOpen.getVersion() != null) {
                            streamFeaturesManager.completeNegotiation()
                                .get(configuration.getDefaultResponseTimeout().toMillis() * 2, TimeUnit.MILLISECONDS);
                        } else {
                            // Wait shortly to see if the server will respond with a <conflict/>, <host-unknown/> or other stream error.
                            Thread.sleep(WAIT_TIMEOUT);
                        }

                        connectedResource = getDomain();
                    }
                }
            }
            throwAsXmppExceptionIfNotNull(exception);
            // Don't call listeners from within synchronized blocks to avoid possible deadlocks.
            updateStatus(Status.CONNECTING, Status.CONNECTED);
            login(sharedSecret);
        } catch (Throwable e) {
            onConnectionFailed(previousStatus, e);
        }
    }

    /**
     * Authenticates with the server using a shared secret.
     *
     * @param sharedSecret The shared secret.
     * @throws XmppException If authentication failed.
     */
    private void login(String sharedSecret) throws XmppException {
        Status previousStatus = preLogin();

        try {
            if (checkAuthenticated()) {
                // Silently return, when we are already authenticated.
                return;
            }
            updateStatus(Status.AUTHENTICATING);
            synchronized (this) {
                handshakeReceived = new CompletableFuture<>();
                if (checkAuthenticated()) {
                    // Silently return, when we are already authenticated.
                    return;
                }
                // Send the <handshake/> element.
                send(Handshake.create(getActiveConnection().getStreamId(), sharedSecret));
                // Wait for the <handshake/> element to be received from the server.
                handshakeReceived.get(configuration.getDefaultResponseTimeout().toMillis(), TimeUnit.MILLISECONDS);
            }
            // Authentication succeeded, update the status.
            updateStatus(Status.AUTHENTICATED);
            // Check if the server returned a stream error, e.g. not-authorized and throw it.
            throwAsXmppExceptionIfNotNull(exception);
            afterLogin();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Revert status
            updateStatus(previousStatus, e);
            throwAsXmppExceptionIfNotNull(e);
        } catch (Throwable e) {
            // Revert status
            updateStatus(previousStatus, e);
            throwAsXmppExceptionIfNotNull(e);
        }

    }

    @Override
    public boolean handleElement(Object element) throws XmppException {
        boolean doRestart = false;
        if (element instanceof Handshake) {
            releaseLock();
        } else {
            doRestart = super.handleElement(element);
        }
        if (element instanceof SessionOpen) {
            CompletableFuture<SessionOpen> future = streamOpened;
            if (future != null) {
                future.complete((SessionOpen) element);
                streamOpened = null;
            }
        }
        return doRestart;
    }

    @Override
    public void notifyException(Throwable e) {
        releaseLock();
        super.notifyException(e);
    }

    private void releaseLock() {
        CompletableFuture<SessionOpen> future = streamOpened;
        if (future != null) {
            future.complete(null);
            streamOpened = null;
        }
        CompletableFuture<Void> future2 = handshakeReceived;
        if (future2 != null) {
            future2.complete(null);
            handshakeReceived = null;
        }
    }

    @Override
    public Jid getConnectedResource() {
        return connectedResource;
    }

    public void setConnectedResource(Jid connectedResource) {
        this.connectedResource = connectedResource;
    }

    @Override
    protected StreamElement prepareElement(StreamElement element) {

        if (element instanceof Stanza && ((Stanza) element).getFrom() == null) {
            ((Stanza) element).setFrom(connectedResource);
        }
        if (element instanceof Message) {
            element = ComponentMessage.from((Message) element);
        } else if (element instanceof Presence) {
            element = ComponentPresence.from((Presence) element);
        } else if (element instanceof IQ) {
            element = ComponentIQ.from((IQ) element);
        }

        return element;
    }
}
