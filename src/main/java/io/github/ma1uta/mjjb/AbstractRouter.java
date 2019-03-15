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

import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.matrix.MatrixServer;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.function.Function;

/**
 * All routers can send response to matrix and xmpp (i. e. normal processing from xmpp to matrix and send errors back to the xmpp).
 *
 * @param <T> Message Type.
 */
public abstract class AbstractRouter<T> implements Function<T, Boolean> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(Loggers.LOGGER);

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

    /**
     * Init router.
     *
     * @param jdbi         persistence service.
     * @param xmppServer   xmpp server.
     * @param matrixServer matrix server.
     */
    public void init(Jdbi jdbi, XmppServer xmppServer, MatrixServer matrixServer) {
        this.jdbi = jdbi;
        this.xmppServer = xmppServer;
        this.matrixServer = matrixServer;
    }

    /**
     * Map MXID to JID.
     *
     * @param mxid MXID.
     * @return JID.
     */
    public String extractJidFromMxid(String mxid) {
        String prefix = getMatrixServer().getConfig().getPrefix();
        try {
            int delim = mxid.indexOf(":");
            String localpart = mxid.substring(1, delim);
            String prepMxid = localpart.startsWith(prefix) ? localpart.substring(prefix.length()) : localpart;
            String encodedJid = prepMxid.replaceAll("=", "%");
            return URLDecoder.decode(encodedJid, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Your JRE doesn't have UTF-8 encoder", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Map JID to MXID.
     *
     * @param jid JID.
     * @return MXID.
     */
    public String extractMxidFromJid(String jid) {
        int localpartIndex = jid.indexOf("@");
        String localpart = localpartIndex == -1 ? jid : jid.substring(0, localpartIndex);

        try {
            return URLDecoder.decode(localpart, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Your JRE doesn't have UTF-8 decoder", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Map JID to MXID.
     *
     * @param jid JID.
     * @return MXID.
     */
    public String encodeJidToMxid(String jid) {
        String encodedJid;
        try {
            encodedJid = URLEncoder.encode(jid, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Your JRE doesn't have UTF-8 decoder", e);
            throw new RuntimeException(e);
        }
        String prepMxid = encodedJid.replaceAll("%", "=");
        MatrixConfig config = getMatrixServer().getConfig();
        return Id.Sigil.USER + config.getPrefix() + prepMxid + ":" + config.getHomeserver();
    }
}
