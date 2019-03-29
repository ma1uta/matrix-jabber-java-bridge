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

package io.github.ma1uta.mjjb.xmpp.sasl;

import io.github.ma1uta.mjjb.Loggers;
import io.github.ma1uta.mjjb.xmpp.IncomingSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.sasl.model.Abort;
import rocks.xmpp.core.sasl.model.Auth;
import rocks.xmpp.core.sasl.model.Challenge;
import rocks.xmpp.core.sasl.model.Failure;
import rocks.xmpp.core.sasl.model.Mechanisms;
import rocks.xmpp.core.sasl.model.Response;
import rocks.xmpp.core.sasl.model.Success;
import rocks.xmpp.core.stream.StreamNegotiationResult;
import rocks.xmpp.core.stream.server.ServerStreamFeatureNegotiator;

import java.util.Collections;

/**
 * Sasl negotiator.
 */
public class SaslNegotiator extends ServerStreamFeatureNegotiator<Mechanisms> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Loggers.LOGGER);

    private Mechanisms feature;
    private IncomingSession session;

    /**
     * Constructs the negotiator.
     */
    public SaslNegotiator(IncomingSession session) {
        super(Mechanisms.class);
        this.session = session;
        feature = new Mechanisms(Collections.singleton("EXTERNAL"));
    }

    @Override
    public Mechanisms createStreamFeature() {
        return feature;
    }

    @Override
    public StreamNegotiationResult processNegotiation(Object element) {
        if (element instanceof Abort) {
            LOGGER.error("[SASL] ABORT.");
            throw new XmppException(new Failure(Failure.Condition.ABORTED));
        }

        return StreamNegotiationResult.IGNORE;
    }

    @Override
    public boolean canProcess(Object element) {
        return element instanceof Auth
            || element instanceof Challenge
            || element instanceof Abort
            || element instanceof Failure
            || element instanceof Response
            || element instanceof Success;
    }
}
