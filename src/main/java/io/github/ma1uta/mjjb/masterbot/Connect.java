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

import io.github.ma1uta.matrix.Event;
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.bot.BotHolder;
import io.github.ma1uta.matrix.bot.Command;
import io.github.ma1uta.matrix.bot.PersistentService;
import io.github.ma1uta.matrix.client.model.room.RoomId;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command to connect current room to the specified conference.
 */
public class Connect implements Command<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void> {

    /**
     * Pattern of the conference url.
     */
    public static final Pattern CONFERENCE_URL = Pattern.compile("(.*)@(.*)");

    @Override
    public String name() {
        return "connect";
    }

    @Override
    public String help() {
        return "connect this room with the specified conference";
    }

    @Override
    public String usage() {
        return "connect <conference url>";
    }

    @Override
    public boolean invoke(BotHolder<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void> holder, String roomId,
                          Event event, String arguments) {

        String inviter = holder.getConfig().getInviters().get(roomId);
        if (inviter == null || !inviter.equals(event.getSender())) {
            return false;
        }

        Matcher matcher = CONFERENCE_URL.matcher(arguments);

        if (!matcher.matches()) {
            holder.getMatrixClient().event().sendNotice(roomId, "Usage: " + usage());
            return false;
        }

        String domain = Id.domain(holder.getConfig().getUserId());

        RoomId roomIdObj = new RoomId();
        roomIdObj.setRoomId(roomId);
        holder.getMatrixClient().room().newAlias(roomIdObj,
            "#" + holder.getConfig().getAliasPrefix() + matcher.group(1) + "_" + matcher.group(2) + ":" + domain);

        return true;
    }
}
