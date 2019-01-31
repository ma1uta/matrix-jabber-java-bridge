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

package io.github.ma1uta.mjjb.xmpp.dialback;

import io.github.ma1uta.mjjb.Loggers;
import io.github.ma1uta.mjjb.xmpp.IncomingSession;
import io.github.ma1uta.mjjb.xmpp.OutgoingSession;
import io.github.ma1uta.mjjb.xmpp.Session;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import org.apache.commons.lang3.RandomStringUtils;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.core.stream.model.StreamHeader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Server ServerDialback.
 * <p/>
 * https://xmpp.org/extensions/xep-0220.html
 */
public class ServerDialback {

    private static final Logger LOGGER = LoggerFactory.getLogger(Loggers.LOGGER);

    /**
     * Dialback namespace.
     */
    public static final String NAMESPACE = "jabber:server:dialback";

    /**
     * Dialback prefix.
     */
    public static final String LOCALPART = "db";

    private static final long CACHE_CAPACITY = 100L;
    private static final int KEY_LENGTH = 12;

    private final XmppServer server;
    private final Map<String, DialbackResult> domains = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private MessageDigest digest;

    private Cache<String, String> keyCache;

    public ServerDialback(XmppServer server) {
        this.server = server;
        this.keyCache = new Cache2kBuilder<String, String>() {
        }
            .name("dialbackKeys")
            .entryCapacity(CACHE_CAPACITY)
            .expireAfterWrite(1L, TimeUnit.HOURS)
            .build();
        try {
            digest = MessageDigest.getInstance("SHA256");
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("JRE doesn't support the SHA256.", e);
            throw new RuntimeException(e);
        }
    }

    public XmppServer getServer() {
        return server;
    }

    /**
     * Generate a new dialback key.
     *
     * @param domain   domain to verification.
     * @param streamId stream id.
     * @return dialback key.
     */
    public String newKey(String domain, String streamId) {
        String secretKey = RandomStringUtils.random(KEY_LENGTH, 0, 0, true, true, null, this.random);
        keyCache.put(domain, secretKey);
        return genKey(streamId, secretKey);
    }

    /**
     * VErify the dialback key.
     *
     * @param domain   domain to verification.
     * @param streamId stream id.
     * @param key      stream secret key.
     * @return {@code true} if domain is passed verification, else {@code false}.
     */
    public boolean verify(String domain, String streamId, String key) {
        String secretKey = keyCache.peek(domain);
        if (secretKey == null) {
            return false;
        }
        return genKey(streamId, secretKey).equals(key);
    }

    protected synchronized String genKey(String streamId, String secretKey) {
        digest.update(streamId.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest.digest(secretKey.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Remove trusted domain.
     *
     * @param domain domain to delete.
     */
    public void remove(String domain) {
        domains.remove(domain);
    }

    /**
     * Negotiation.
     *
     * @param session       xmpp session.
     * @param streamElement stream element.
     * @return {@code true} if domain is trusted else {@code false}.
     */
    public boolean negotiate(Session session, Object streamElement) {
        return session instanceof IncomingSession
            ? negotiateIncoming((IncomingSession) session, streamElement)
            : negotiateOutgoing((OutgoingSession) session, streamElement);
    }

    /**
     * Negotiate with the outgoing connection.
     *
     * @param session       outgoing session.
     * @param streamElement response from the remote server.
     * @return {@code true} if connection is verified and trusted else {@code false}.
     */
    public boolean negotiateOutgoing(OutgoingSession session, Object streamElement) {
        String domain = session.getJid().getDomain();
        DialbackResult status = domains.get(domain);

        // pass if validated.
        if (status == DialbackResult.TRUSTED) {
            return true;
        }

        // initiate dialback negotiation.
        if (status == null && streamElement instanceof StreamHeader) {
            StreamHeader header = (StreamHeader) streamElement;
            session.getConnection().send(new Result(header.getFrom(), header.getTo(), newKey(domain, header.getId()), null));
            domains.put(domain, DialbackResult.SENT);
            return false;
        }

        if (status == DialbackResult.SENT && streamElement instanceof Result) {
            Result result = (Result) streamElement;
            if (DialbackElement.DialbackType.valid == result.getType()) {
                domains.put(domain, DialbackResult.TRUSTED);
                return true;
            }
        }

        return false;
    }

    /**
     * Negotiate with the incoming connection.
     *
     * @param session       incoming session.
     * @param streamElement response from the remote server.
     * @return {@code true} if connection is verified and trusted else {@code false}.
     */
    public boolean negotiateIncoming(IncomingSession session, Object streamElement) {
        if (streamElement instanceof Result) {
            Result result = (Result) streamElement;
            getServer().send(result.getTo(), new Verify(result.getFrom(), result.getTo(), result.getText(), null));
            return false;
        }

        if (streamElement instanceof Verify) {
            Verify verify = (Verify) streamElement;
            String key = keyCache.get(verify.getFrom().getDomain());
            DialbackElement.DialbackType type = DialbackElement.DialbackType.invalid;
            if (key != null && key.equals(verify.getText())) {
                type = DialbackElement.DialbackType.valid;
            }
            getServer().send(verify.getFrom(), new Result(verify.getFrom(), verify.getTo(), null, type));
            return false;
        }

        return true;
    }

    /**
     * Dialback states.
     */
    public enum DialbackResult {

        /**
         * Domain was verified and trusted.
         */
        TRUSTED,

        /**
         * Dialback key was sending to the Receiving server.
         */
        SENT,

        /**
         * Dialback key was verified.
         */
        VERIFIED,
    }


}
