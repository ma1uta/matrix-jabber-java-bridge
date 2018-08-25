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
import io.github.ma1uta.matrix.bot.BotHolder;
import io.github.ma1uta.matrix.bot.Command;
import io.github.ma1uta.matrix.bot.PersistentService;
import io.github.ma1uta.mjjb.transport.Transport;

/**
 * Command to disconnect current room to the specified conference.
 */
public class Disconnect implements Command<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void> {

    @Override
    public String name() {
        return "disconnect";
    }

    @Override
    public String help() {
        return "disconnect this room from the connected conference";
    }

    @Override
    public String usage() {
        return "disconnect";
    }

    @Override
    public boolean invoke(BotHolder<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void> holder, String roomId,
                          Event event, String arguments) {

        String inviter = holder.getConfig().getInviters().get(roomId);
        if (inviter == null || !inviter.equals(event.getSender())) {
            return false;
        }

        MasterBot bot = (MasterBot) holder.getBot();

        Transport transport = bot.getPool().getTransports().remove(roomId);
        if (transport != null) {
            transport.remove();
            return true;
        }

        return false;
    }
}
