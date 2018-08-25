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

package io.github.ma1uta.mjjb.config;

import io.github.ma1uta.matrix.bot.Command;
import io.github.ma1uta.matrix.bot.PersistentService;
import io.github.ma1uta.mjjb.masterbot.MasterBotConfig;
import io.github.ma1uta.mjjb.masterbot.MasterBotDao;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

import java.util.List;

/**
 * Configuration of the matrix AS.
 */
public class MatrixConfig {

    @URL
    @NotBlank
    private String homeserver;

    @NotBlank
    private String accessToken;

    @NotBlank
    private String masterUserId;

    @NotBlank
    private String prefix;

    private List<Class<? extends Command<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void>>> commands;

    public String getHomeserver() {
        return homeserver;
    }

    public void setHomeserver(String homeserver) {
        this.homeserver = homeserver;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getMasterUserId() {
        return masterUserId;
    }

    public void setMasterUserId(String masterUserId) {
        this.masterUserId = masterUserId;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public List<Class<? extends Command<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void>>> getCommands() {
        return commands;
    }

    public void setCommands(
        List<Class<? extends Command<MasterBotConfig, MasterBotDao, PersistentService<MasterBotDao>, Void>>> commands) {
        this.commands = commands;
    }
}
