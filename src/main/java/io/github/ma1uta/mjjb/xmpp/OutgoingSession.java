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
import rocks.xmpp.core.stream.model.StreamHeader;

import javax.xml.bind.JAXBException;

/**
 * XMPP S2S outgoing session.
 */
public class OutgoingSession extends Session {

    public OutgoingSession(XmppServer xmppServer, Jid jid) throws JAXBException {
        super(xmppServer);
        setJid(jid);
    }

    /**
     * Handle stream (open, close, restart).
     *
     * @param streamElement stream stanza.
     * @return {@code true} to restart stream, else {@code false}.
     */
    public boolean handleStream(Object streamElement) {
        if (super.handleStream(streamElement)) {
            return true;
        }
        return false;
    }

    public void handshake() {
        getConnection().send(StreamHeader.initialServerToServer(Jid.of(getXmppServer().getConfig().getDomain()), getJid(), null));
    }
}
