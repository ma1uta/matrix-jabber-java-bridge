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

import io.github.ma1uta.matrix.bot.BotDao;
import io.github.ma1uta.matrix.bot.BotState;
import io.github.ma1uta.matrix.bot.ReceiptPolicy;
import org.apache.commons.lang3.tuple.Pair;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DAO for works with the config of the master bot via JDBI.
 */
public class MasterBotDao implements BotDao<MasterBotConfig> {

    private final Jdbi jdbi;

    public MasterBotDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    @Override
    public List<MasterBotConfig> findAll() {
        return getJdbi().inTransaction(handle -> handle.createQuery("select * from bot_config")
            .map(new ConfigMapper())
            .stream().peek(
                config -> handle.createQuery("select * from inviter where master_id = :master_id").bind("master_id", config.getUserId())
                    .map((rs, ctx) -> Pair.of(rs.getString("room_id"), rs.getString("user_id")))
                    .forEach(pair -> config.getInviters().put(pair.getKey(), pair.getValue()))
            ).collect(Collectors.toList())
        );
    }

    @Override
    public boolean user(String userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MasterBotConfig save(MasterBotConfig data) {
        return getJdbi().inTransaction(handle -> {
            handle.createUpdate(
                "insert into bot_config(user_id, device_id, display_name, state, filter_id, alias_prefix) "
                    + " values(:userId, :deviceId, :displayName, :state, :filterId, :aliasPrefix) "
                    + " on conflict do update bot_config set user_id = :userId, device_id = :deviceId, display_name = :displayName, "
                    + " state = :state, filter_id = :filterId, alias_prefix = :aliasPrefix where user_id = :userId")
                .bindBean(data).execute();
            handle.createUpdate("delete from inviters where master_id = :master_id").bind("master_id", data.getUserId());
            data.getInviters().forEach((roomId, userId) -> handle.createUpdate(
                "insert into inviters(master_id, room_id, user_id) values(:master_id, :room_id, :user_id) on conflict do nothing")
                .bind("master_id", data.getUserId())
                .bind("room_id", roomId)
                .bind("user_id", userId).execute());
            return data;
        });
    }

    @Override
    public void delete(MasterBotConfig data) {
        getJdbi().useTransaction(
            handle -> {
                handle.createUpdate("delete from inviters where master_id = :master_id").bind("master_id", data.getUserId()).execute();
                handle.createUpdate("delete from bot_config where user_id = :userId").bind("userId", data.getUserId()).execute();
            });
    }

    /**
     * Mapper of the bot config.
     */
    public static class ConfigMapper implements RowMapper<MasterBotConfig> {

        @Override
        public MasterBotConfig map(ResultSet rs, StatementContext ctx) throws SQLException {
            MasterBotConfig config = new MasterBotConfig();
            config.setId(rs.getLong("id"));
            config.setUserId(rs.getString("user_id"));
            config.setDeviceId(rs.getString("device_id"));
            config.setDisplayName(rs.getString("display_name"));
            config.setState(BotState.valueOf(rs.getString("state")));
            config.setFilterId(rs.getString("filter_id"));
            config.setAliasPrefix(rs.getString("alias_prefix"));
            config.setReceiptPolicy(ReceiptPolicy.EXECUTED);
            config.setPrefix("!");
            return config;
        }
    }
}
