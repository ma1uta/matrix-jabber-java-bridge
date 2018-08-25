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

package io.github.ma1uta.mjjb.masterbot;

import static io.github.ma1uta.mjjb.dao.RoomAliasDao.ROOM_PATTERN;

import io.github.ma1uta.matrix.Event;
import io.github.ma1uta.matrix.EventContent;
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.bot.ApplicationServiceBot;
import io.github.ma1uta.matrix.bot.Command;
import io.github.ma1uta.matrix.bot.PersistentService;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.model.room.CreateRoomRequest;
import io.github.ma1uta.matrix.events.RoomAliases;
import io.github.ma1uta.matrix.events.RoomMember;
import io.github.ma1uta.matrix.events.RoomMessage;
import io.github.ma1uta.mjjb.transport.Transport;
import io.github.ma1uta.mjjb.transport.TransportPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.core.XmppException;

import java.util.List;
import java.util.Optional;
import javax.ws.rs.client.Client;

/**
 * Master bot.
 */
public class MasterBot extends ApplicationServiceBot<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MasterBot.class);

    private TransportPool pool;

    public MasterBot(Client client, String homeserverUrl, String asToken, MasterBotConfig config, PersistentService<MasterBotDao> service,
                     List<Class<? extends Command<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void>>> commandsClasses) {
        super(client, homeserverUrl, asToken, false, config, service, commandsClasses);
    }

    public TransportPool getPool() {
        return pool;
    }

    public void setPool(TransportPool pool) {
        this.pool = pool;
    }

    @Override
    protected boolean permit(Event event) {
        return true;
    }

    @Override
    public void send(Event event) {
        EventContent content = event.getContent();
        boolean commandInvoked = false;
        if (content instanceof RoomMember) {
            commandInvoked = joinOrLeave(event);
        } else if (content instanceof RoomMessage) {
            commandInvoked = processEvent(event.getRoomId(), event);
        } else if (content instanceof RoomAliases) {
            commandInvoked = createOrRemoveTransport(event, (RoomAliases) content);
        }

        if (!commandInvoked) {
            getPool().event(event);
        }
    }

    protected boolean createOrRemoveTransport(Event event, RoomAliases aliases) {
        Optional<String> foundAlias = aliases.getAliases().stream()
            .filter(alias -> ROOM_PATTERN.matcher(Id.localpart(alias)).matches())
            .findAny();
        if (foundAlias.isPresent()) {
            String alias = foundAlias.get();
            try {
                getPool().runTransport(event.getRoomId(), alias);
                return true;
            } catch (XmppException e) {
                LOGGER.error(String.format("Cannot create transport in the room %s with conference %s", event.getRoomId(), alias), e);
            }
        } else {
            Transport transportToRemove = getPool().getTransports().remove(event.getRoomId());
            if (transportToRemove != null) {
                transportToRemove.remove();
                return true;
            }
        }
        return false;
    }

    protected boolean joinOrLeave(Event event) {
        RoomMember content = (RoomMember) event.getContent();

        if (!getHolder().getConfig().getUserId().equals(event.getStateKey())) {
            return false;
        }

        switch (content.getMembership()) {
            case Event.MembershipState.INVITE:
                getHolder().runInTransaction((holder, dao) -> {
                    holder.getConfig().getInviters().put(event.getRoomId(), event.getSender());
                    holder.getMatrixClient().room().joinByIdOrAlias(event.getRoomId());
                });
                return true;
            case Event.MembershipState.BAN:
            case Event.MembershipState.LEAVE:
                getHolder().runInTransaction((holder, dao) -> {
                    holder.getConfig().getInviters().remove(event.getRoomId());
                    getPool().getTransports().remove(event.getRoomId()).remove();
                });
                return true;
            default:
                return false;
        }
    }

    /**
     * Create new room with specified alias.
     *
     * @param roomAlias room alias.
     */
    public void createTransport(String roomAlias) {
        CreateRoomRequest request = new CreateRoomRequest();
        request.setVisibility(Event.Visibility.SHARED);
        request.setRoomAliasName(roomAlias);
        request.setGuestCanJoin(true);
        request.setDirect(false);
        MatrixClient client = getHolder().getMatrixClient();

        client.room().create(request);
        client.room().joinByIdOrAlias(roomAlias);
    }
}
