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

package io.github.ma1uta.mjjb.matrix.router;

import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.event.RoomMessage;
import io.github.ma1uta.matrix.event.content.RoomMessageContent;
import io.github.ma1uta.mjjb.AbstractRouter;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.db.DirectRoom;
import io.github.ma1uta.mjjb.db.RoomDao;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import org.jdbi.v3.core.Jdbi;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stanza.model.Message;
import rocks.xmpp.core.stanza.model.server.ServerMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Matrix to XMPP message router.
 */
public class MessageRouter extends AbstractRouter<RoomMessage<?>> {

    private final Map<Class<? extends RoomMessageContent>, BiFunction<Jid, RoomMessage, Message>> converters = new HashMap<>();

    public MessageRouter(Jdbi jdbi, XmppServer xmppServer, MatrixConfig matrixConfig, MatrixClient matrixClient,
                         Map<Class<? extends RoomMessageContent>, BiFunction<Jid, RoomMessage, Message>> converters) {
        super(jdbi, xmppServer, matrixConfig, matrixClient);
        this.converters.putAll(converters);
    }

    public BiFunction<Jid, RoomMessage, Message> getConverter(Class<? extends RoomMessageContent> key) {
        return converters.get(key);
    }

    @Override
    public Boolean apply(RoomMessage<?> message) {
        BiFunction<Jid, RoomMessage, Message> converter = getConverter(message.getContent().getClass());
        if (converter != null) {
            getJdbi().useExtension(RoomDao.class, roomDao -> {
                DirectRoom room = roomDao.findDirectRoom(message.getRoomId());
                if (room != null) {
                    ServerMessage xmppMessage = ServerMessage.from(converter.apply(room.getXmppJid(), message));
                    xmppMessage.setFrom(mxidToJid(message.getSender()));

                    getXmppServer().send(xmppMessage);
                } else {
                    //TODO not found room. Create new one?
                }
            });
        }
        return null;
    }
}
