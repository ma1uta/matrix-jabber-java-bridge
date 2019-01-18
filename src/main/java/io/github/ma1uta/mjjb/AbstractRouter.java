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

package io.github.ma1uta.mjjb;

import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.matrix.MatrixServer;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.function.Function;

/**
 * All routers can send response to matrix and xmpp (i. e. normal processing from xmpp to matrix and send errors back to the xmpp).
 *
 * @param <T> Message Type.
 */
public abstract class AbstractRouter<T> implements Function<T, Boolean> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Loggers.LOGGER);

    private Jdbi jdbi;
    private XmppServer xmppServer;
    private MatrixServer matrixServer;

    public Jdbi getJdbi() {
        return jdbi;
    }

    public XmppServer getXmppServer() {
        return xmppServer;
    }

    public MatrixServer getMatrixServer() {
        return matrixServer;
    }

    public void init(Jdbi jdbi, XmppServer xmppServer, MatrixServer matrixServer) {
        this.jdbi = jdbi;
        this.xmppServer = xmppServer;
        this.matrixServer = matrixServer;
    }

    public String mxidToJid(String mxid) {
        String prefix = getMatrixServer().getConfig().getPrefix();
        String domain = getXmppServer().getConfig().getDomain();
        try {
            return prefix + URLEncoder.encode(mxid, "UTF-8") + "@" + domain;
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Your JRE doesn't have UTF-8 encoder", e);
            throw new RuntimeException(e);
        }
    }
}
