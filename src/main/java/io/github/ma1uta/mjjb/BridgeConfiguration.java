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

package io.github.ma1uta.mjjb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.db.DataSourceFactory;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.config.XmppConfig;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Application configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BridgeConfiguration extends Configuration {

    @NotNull
    private XmppConfig xmpp;

    @NotNull
    private MatrixConfig matrix;

    @Valid
    @JsonProperty("httpClient")
    private JerseyClientConfiguration httpClient = new JerseyClientConfiguration();

    @Valid
    @NotNull
    @JsonProperty("database")
    private DataSourceFactory database = new DataSourceFactory();

    @NotNull
    public XmppConfig getXmpp() {
        return xmpp;
    }

    public void setXmpp(@NotNull XmppConfig xmpp) {
        this.xmpp = xmpp;
    }

    @NotNull
    public MatrixConfig getMatrix() {
        return matrix;
    }

    public void setMatrix(@NotNull MatrixConfig matrix) {
        this.matrix = matrix;
    }

    @NotNull
    public JerseyClientConfiguration getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(@NotNull JerseyClientConfiguration httpClient) {
        this.httpClient = httpClient;
    }

    @NotNull
    public DataSourceFactory getDatabase() {
        return database;
    }

    public void setDatabase(@NotNull DataSourceFactory database) {
        this.database = database;
    }
}
