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
import io.github.ma1uta.mjjb.model.RoomAlias;
import io.github.ma1uta.mjjb.transport.Transport;
import org.jsoup.Jsoup;

/**
 * Command to show info of the bridge.
 */
public class Info implements Command<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void> {

    @Override
    public String name() {
        return "info";
    }

    @Override
    public String help() {
        return "show info";
    }

    @Override
    public String usage() {
        return "info";
    }

    @Override
    public boolean invoke(BotHolder<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void> holder, String roomId,
                          Event event, String arguments) {
        MasterBot bot = (MasterBot) holder.getBot();
        Transport transport = bot.getPool().getTransports().get(roomId);
        if (transport != null) {
            StringBuilder sb = new StringBuilder();
            RoomAlias roomAlias = transport.getRoomAlias();
            sb.append("Matrix room id: ").append(roomAlias.getRoomId()).append("<br>");
            sb.append("Matrix room alias: ").append(roomAlias.getAlias()).append("<br>");
            sb.append("Xmpp conference: ").append(roomAlias.getConferenceJid()).append("<br>");

            sb.append("Puppet users:<br>Matrix -> Xmpp:<br>");
            transport.getMxToXmppUsers().forEach((userId, nick) -> sb.append(userId).append(" -> ").append(nick).append("<br>"));
            sb.append("Xmpp -> Matrix:<br>");
            transport.getXmppToMxUsers().forEach((nick, userId) -> sb.append(nick).append(" -> ").append(userId).append("<br>"));

            String formatted = sb.toString();
            holder.getMatrixClient().event().sendFormattedNotice(roomId, Jsoup.parse(formatted).text(), formatted);
        }

        return true;
    }
}
