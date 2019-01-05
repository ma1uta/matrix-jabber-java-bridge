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

package io.github.ma1uta.mjjb;

import io.github.ma1uta.matrix.ErrorResponse;
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.model.account.RegisterRequest;
import io.github.ma1uta.matrix.event.Event;
import io.github.ma1uta.matrix.event.RoomMessage;
import io.github.ma1uta.matrix.event.content.RoomMessageContent;
import io.github.ma1uta.matrix.impl.exception.MatrixException;
import io.github.ma1uta.mjjb.config.AppConfig;
import io.github.ma1uta.mjjb.db.DirectRoom;
import io.github.ma1uta.mjjb.db.RoomDao;
import io.github.ma1uta.mjjb.db.UserDao;
import io.github.ma1uta.mjjb.xmpp.netty.XmppServer;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stanza.model.Message;
import rocks.xmpp.core.stanza.model.server.ServerMessage;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class Transport {

    private static final Logger LOGGER = LoggerFactory.getLogger(Transport.class);

    private final AppConfig config;
    private final Jdbi jdbi;

    private final MatrixClient matrixClient;
    private XmppServer xmppServer;
    private final Map<Class<? extends RoomMessageContent>, BiFunction<Jid, RoomMessage, Message>> converters = new HashMap<>();

    public Transport(AppConfig config, Jdbi jdbi, MatrixClient matrixClient) {
        this.config = config;
        this.jdbi = jdbi;
        this.matrixClient = matrixClient;
    }

    public AppConfig getConfig() {
        return config;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public MatrixClient getMatrixClient() {
        return matrixClient;
    }

    public XmppServer getXmppServer() {
        return xmppServer;
    }

    public void setXmppServer(XmppServer xmppServer) {
        this.xmppServer = xmppServer;
    }

    public Map<Class<? extends RoomMessageContent>, BiFunction<Jid, RoomMessage, Message>> getConverters() {
        return converters;
    }

    public BiFunction<Jid, RoomMessage, Message> getConverter(Class<? extends RoomMessageContent> key) {
        return getConverters().get(key);
    }

    public Jid mxidToJid(String mxid) {
        try {
            return Jid.of(getConfig().getMatrix().getPrefix() + URLEncoder.encode(mxid, "UTF-8") + "@" + getConfig().getXmpp().getDomain());
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Your JRE doesn't have UTF-8 encoder", e);
            throw new RuntimeException(e);
        }
    }

    public void process(Event<?> event) {
        if (event instanceof RoomMessage) {
            processMessageEvent((RoomMessage<?>) event);
        }
    }

    public void createUser(String userId) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(userId);
        request.setInhibitLogin(false);

        getMatrixClient().account().register(request).whenCompleteAsync((resp, exc) -> {
            if (exc != null) {
                LOGGER.error(String.format("Failed create new user: %s", userId), exc);
                throw new MatrixException(ErrorResponse.Code.M_UNKNOWN, exc.getMessage());
            }
            getJdbi().useExtension(UserDao.class, dao -> dao.create(Id.getInstance().localpart(resp.getUserId())));
        }).join();
    }

    public void createRoom(String roomId) {

    }

    private void processMessageEvent(RoomMessage<?> roomMessage) {
        BiFunction<Jid, RoomMessage, Message> converter = getConverter(roomMessage.getContent().getClass());
        if (converter != null) {
            getJdbi().useExtension(RoomDao.class, roomDao -> {
                DirectRoom room = roomDao.findDirectRoom(roomMessage.getRoomId());
                if (room != null) {
                    ServerMessage xmppMessage = ServerMessage.from(converter.apply(room.getXmppJid(), roomMessage));
                    xmppMessage.setFrom(mxidToJid(roomMessage.getSender()));

                    getXmppServer().send(xmppMessage);
                } else {
                    //TODO not found room. Create new one?
                }
            });
        }
    }
}
