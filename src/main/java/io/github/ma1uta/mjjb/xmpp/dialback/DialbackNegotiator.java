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

package io.github.ma1uta.mjjb.xmpp.dialback;

import io.github.ma1uta.mjjb.xmpp.XmppServer;
import rocks.xmpp.core.net.TcpBinding;
import rocks.xmpp.core.stream.StreamNegotiationResult;
import rocks.xmpp.core.stream.server.ServerStreamFeatureNegotiator;

/**
 * Dialback negotiator of the incoming sessions.
 */
public class DialbackNegotiator extends ServerStreamFeatureNegotiator<Dialback> {

    private final TcpBinding connection;
    private final XmppServer server;

    /**
     * Constructs the negotiator.
     */
    public DialbackNegotiator(TcpBinding connection, XmppServer server) {
        super(Dialback.class);
        this.connection = connection;
        this.server = server;
    }

    @Override
    public Dialback createStreamFeature() {
        return Dialback.INSTANCE;
    }

    @Override
    public StreamNegotiationResult processNegotiation(Object element) {
        switch (server.dialback().negotiateIncoming(connection, element)) {
            case IN_PROCESS:
                return StreamNegotiationResult.INCOMPLETE;
            case FAILED:
                connection.closeAsync();
                break;
            case IGNORED:
            default:
                // nothing to do
        }
        return StreamNegotiationResult.IGNORE;
    }

    @Override
    public boolean canProcess(Object element) {
        return element instanceof Result || element instanceof Verify;
    }
}
