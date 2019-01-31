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

import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.stanza.model.Stanza;
import rocks.xmpp.core.stream.model.StreamFeatures;
import rocks.xmpp.core.stream.model.StreamHeader;

import java.util.Locale;
import java.util.UUID;
import javax.xml.bind.JAXBException;

/**
 * XMPP S2S incoming session.
 */
public class IncomingSession extends Session {

    public IncomingSession(XmppServer xmppServer) throws JAXBException {
        super(xmppServer);
    }

    @Override
    public boolean handleStream(Object streamElement) throws XmppException {
        if (super.handleStream(streamElement)) {
            return true;
        }
        if (streamElement instanceof StreamHeader) {
            StreamHeader streamHeader = (StreamHeader) streamElement;
            getXmppServer().establish(streamHeader.getFrom(), this);
            // send stream header response
            getConnection().send(StreamHeader.responseServerToServer(
                Jid.of(getXmppServer().getConfig().getDomain()),
                streamHeader.getFrom(),
                UUID.randomUUID().toString(),
                Locale.getDefault()
            ));
            // send supported features.
            getConnection().send(new StreamFeatures(getStreamFeaturesManager().getStreamFeatures()));
        }
        if (streamElement instanceof Stanza) {
            getXmppServer().process((Stanza) streamElement);
        }
        return false;
    }
}
