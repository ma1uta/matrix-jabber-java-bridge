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

package io.github.ma1uta.mjjb.config;

/**
 * XMPP side config.
 */
public class XmppConfig {

    /**
     * Default xmpp server-to-server port.
     */
    public static final int DEFAULT_S2S_PORT = 5269;

    private String domain = "localhost";

    private int port = DEFAULT_S2S_PORT;

    private Cert ssl;

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Cert getSsl() {
        return ssl;
    }

    public void setSsl(Cert ssl) {
        this.ssl = ssl;
    }
}
