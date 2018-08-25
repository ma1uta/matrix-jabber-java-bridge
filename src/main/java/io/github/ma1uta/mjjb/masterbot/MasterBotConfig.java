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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import io.github.ma1uta.matrix.bot.BotConfig;

/**
 * Additional configuration of the master bot.
 */
public class MasterBotConfig extends BotConfig {

    /**
     * Who invited the bot.
     */
    private BiMap<String, String> inviters = HashBiMap.create();

    /**
     * Prefix of the aliases.
     */
    private String aliasPrefix;

    public BiMap<String, String> getInviters() {
        return inviters;
    }

    public String getAliasPrefix() {
        return aliasPrefix;
    }

    public void setAliasPrefix(String aliasPrefix) {
        this.aliasPrefix = aliasPrefix;
    }
}
