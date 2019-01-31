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

import io.github.ma1uta.mjjb.xmpp.OutgoingSession;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import io.netty.channel.Channel;
import rocks.xmpp.addr.Jid;

/**
 * XMPP server netty channel initializer.
 */
public class XmppClientInitializer extends XmppNettyInitializer<Channel, OutgoingSession> {

    private final Jid jid;

    public XmppClientInitializer(XmppServer xmppServer, Jid jid) {
        super(xmppServer);
        this.jid = jid;
    }

    @Override
    protected OutgoingSession createSession() throws Exception {
        return new OutgoingSession(getServer(), jid);
    }

    @Override
    protected void notifyServer(OutgoingSession session) {
        getServer().newOutgoingSession(session);
    }

    @Override
    protected void initConnection(Channel channel, OutgoingSession session) {
        super.initConnection(channel, session);
        session.setExecutor(channel.eventLoop());
    }
}
