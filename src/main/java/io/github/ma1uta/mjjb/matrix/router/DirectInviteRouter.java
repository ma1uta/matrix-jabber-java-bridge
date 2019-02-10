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

import io.github.ma1uta.matrix.event.RoomMember;
import io.github.ma1uta.matrix.event.content.RoomMemberContent;
import io.github.ma1uta.mjjb.AbstractRouter;
import io.github.ma1uta.mjjb.db.DirectRoom;
import io.github.ma1uta.mjjb.db.RoomDao;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stanza.model.Presence;
import rocks.xmpp.core.stanza.model.server.ServerPresence;

/**
 * Process incoming matrix invite requests.
 */
public class DirectInviteRouter extends AbstractRouter<RoomMember> {

    @Override
    public Boolean apply(RoomMember roomMember) {
        RoomMemberContent content = roomMember.getContent();
        if (content.getDirect() == null || !content.getDirect()) {
            return false;
        }

        if (!RoomMemberContent.INVITE.equals(content.getMembership())) {
            return false;
        }

        String prefix = getMatrixServer().getConfig().getPrefix();
        String invitedUser = roomMember.getStateKey();

        if (!invitedUser.startsWith("@" + prefix)) {
            return false;
        }

        return getJdbi().inTransaction(h -> {
            RoomDao roomDao = h.attach(RoomDao.class);
            String roomId = roomMember.getRoomId().toString();
            DirectRoom room = roomDao.findDirectRoomByRoomId(roomId);
            String jid = extractJidFromMxid(invitedUser);
            if (room == null) {
                roomDao.createDirectRoom(roomId, roomMember.getSender().toString(), jid);
            }
            roomDao.updateMatrixSubscription(roomId, true);

            try {
                Presence presence = new Presence(Jid.of(jid), Presence.Type.SUBSCRIBE, null);
                getXmppServer().send(ServerPresence.from(presence));
            } catch (Exception e) {
                LOGGER.error("Unable to send message.", e);
                return false;
            }
            return true;
        });
    }
}
