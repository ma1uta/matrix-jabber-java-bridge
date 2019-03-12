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

import io.github.ma1uta.matrix.event.RoomMessage;
import io.github.ma1uta.matrix.event.content.RoomMessageContent;
import io.github.ma1uta.mjjb.AbstractRouter;
import io.github.ma1uta.mjjb.db.DirectRoom;
import io.github.ma1uta.mjjb.db.RoomDao;
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

    private Map<Class<? extends RoomMessageContent>, BiFunction<Jid, RoomMessage<?>, Message>> converters = new HashMap<>();

    /**
     * Provides message converters.
     *
     * @param key message class.
     * @return converter.
     */
    public BiFunction<Jid, RoomMessage<?>, Message> getConverter(Class<? extends RoomMessageContent> key) {
        return converters.get(key);
    }

    public void setConverters(Map<Class<? extends RoomMessageContent>, BiFunction<Jid, RoomMessage<?>, Message>> converters) {
        this.converters = converters;
    }

    @Override
    public Boolean apply(RoomMessage<?> message) {
        BiFunction<Jid, RoomMessage<?>, Message> converter = getConverter(message.getContent().getClass());
        if (converter == null) {
            return false;
        }

        return getJdbi().inTransaction(h -> {
            RoomDao roomDao = h.attach(RoomDao.class);
            DirectRoom room = roomDao.findDirectRoomByRoomId(message.getRoomId());
            if (room == null) {
                return false;
            }

            ServerMessage xmppMessage = ServerMessage.from(converter.apply(room.getXmppJid(), message));
            xmppMessage.setFrom(Jid.of(extractJidFromMxid(message.getSender())));

            try {
                getXmppServer().send(xmppMessage);
                return true;
            } catch (Exception e) {
                LOGGER.error("Unable to send message.", e);
                return false;
            }
        });
    }
}
