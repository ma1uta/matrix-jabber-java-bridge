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

package io.github.ma1uta.mjjb.transport;

import io.github.ma1uta.mjjb.BridgeConfiguration;

import java.util.Objects;

/**
 * Configuration for the transport.
 */
public class TransportConfiguration {

    /**
     * Default xmpp port.
     */
    public static final int DEFAULT_XMPP_PORT = 5222;

    private String xmppComponentName;

    private String xmppShareSecret;

    private String xmppHostName;

    private int xmppPort;

    private String matrixHomeserver;

    private String accessToken;

    private String masterUserId;

    private String prefix;

    public TransportConfiguration(BridgeConfiguration config) {
        this.xmppComponentName = Objects.requireNonNull(config.getXmpp().getComponentName());
        this.xmppShareSecret = Objects.requireNonNull(config.getXmpp().getSharedSecret());
        this.xmppHostName = Objects.requireNonNull(config.getXmpp().getHostname());
        this.xmppPort = config.getXmpp().getPort();

        this.matrixHomeserver = Objects.requireNonNull(config.getMatrix().getHomeserver());
        this.accessToken = Objects.requireNonNull(config.getMatrix().getAsToken());
        this.masterUserId = Objects.requireNonNull(config.getMatrix().getMasterUserId());
        this.prefix = Objects.requireNonNull(config.getMatrix().getPrefix());
    }

    public String getXmppComponentName() {
        return xmppComponentName;
    }

    public void setXmppComponentName(String xmppComponentName) {
        this.xmppComponentName = xmppComponentName;
    }

    public String getXmppShareSecret() {
        return xmppShareSecret;
    }

    public void setXmppShareSecret(String xmppShareSecret) {
        this.xmppShareSecret = xmppShareSecret;
    }

    public String getXmppHostName() {
        return xmppHostName;
    }

    public void setXmppHostName(String xmppHostName) {
        this.xmppHostName = xmppHostName;
    }

    public int getXmppPort() {
        return xmppPort;
    }

    public void setXmppPort(int xmppPort) {
        this.xmppPort = xmppPort;
    }

    public String getMatrixHomeserver() {
        return matrixHomeserver;
    }

    public void setMatrixHomeserver(String matrixHomeserver) {
        this.matrixHomeserver = matrixHomeserver;
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
}
