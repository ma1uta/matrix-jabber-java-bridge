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

import io.github.ma1uta.mjjb.xmpp.dialback.Dialback;
import io.github.ma1uta.mjjb.xmpp.dialback.DialbackError;
import io.github.ma1uta.mjjb.xmpp.dialback.Result;
import io.github.ma1uta.mjjb.xmpp.dialback.Verify;
import rocks.xmpp.core.sasl.model.Mechanisms;
import rocks.xmpp.core.session.model.Session;
import rocks.xmpp.core.stanza.model.server.ServerIQ;
import rocks.xmpp.core.stanza.model.server.ServerMessage;
import rocks.xmpp.core.stanza.model.server.ServerPresence;
import rocks.xmpp.core.stream.model.StreamError;
import rocks.xmpp.core.stream.model.StreamFeatures;
import rocks.xmpp.core.tls.model.StartTls;
import rocks.xmpp.extensions.compress.model.StreamCompression;

import javax.xml.bind.DataBindingException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * Default JAXB configuration.
 */
public final class ServerConfiguration {

    /**
     * Default JAXB context.
     */
    public static final JAXBContext JAXB_CONTEXT;

    static {
        try {
            JAXB_CONTEXT = JAXBContext.newInstance(
                StreamFeatures.class,
                StreamError.class,
                ServerMessage.class,
                ServerPresence.class,
                ServerIQ.class,
                Session.class,
                Mechanisms.class,
                StartTls.class,
                StreamCompression.class,
                Dialback.class,
                DialbackError.class,
                Result.class,
                Verify.class
            );
        } catch (JAXBException e) {
            throw new DataBindingException(e);
        }
    }

    private ServerConfiguration() {
        // singleton.
    }
}
