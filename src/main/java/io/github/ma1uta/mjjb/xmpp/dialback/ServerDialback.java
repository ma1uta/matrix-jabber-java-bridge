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
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import org.apache.commons.lang3.RandomStringUtils;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.net.TcpBinding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
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
    public static final String PREFIX = "db";

    private static final long CACHE_CAPACITY = 100L;
    private static final int KEY_LENGTH = 12;

    private final XmppServer server;
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
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("JRE doesn't support the SHA-256.", e);
            throw new RuntimeException(e);
        }
    }

    public XmppServer getServer() {
        return server;
    }

    /**
     * Generate a new dialback key.
     *
     * @param streamId stream id.
     * @return dialback key.
     */
    public String newKey(String streamId) {
        String secretKey = RandomStringUtils.random(KEY_LENGTH, 0, 0, true, true, null, this.random);
        keyCache.put(streamId, secretKey);
        return genKey(streamId, secretKey);
    }

    /**
     * Verify the dialback key.
     *
     * @param streamId stream id.
     * @param key      stream secret key.
     * @return {@code true} if domain is passed verification, else {@code false}.
     */
    public boolean verify(String streamId, String key) {
        String secretKey = keyCache.peek(streamId);
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
     * Negotiate with the outgoing connection.
     *
     * @param session       outgoing session.
     * @param streamElement response from the remote server.
     * @return {@code true} if connection is verified and trusted else {@code false}.
     */
    public DialbackNegotiationResult negotiateOutgoing(OutgoingSession session, Object streamElement) {
        State status = session.dialback();

        // pass if validated.
        if (State.TRUSTED == status || State.DISABLED == status) {
            return DialbackNegotiationResult.SUCCESS;
        }

        // initiate dialback negotiation.
        if (State.SUPPORT.equals(status)) {
            session.dialback(State.SENT);
            session.sendDirect(new Result(
                UUID.randomUUID().toString(),                       // id
                Jid.of(session.getDomain()),                        // to
                Jid.of(getServer().getConfig().getDomain()),        // from
                newKey(session.getConnection().getStreamId()),      // key
                null));
            return DialbackNegotiationResult.IN_PROCESS;
        }

        if (status == State.SENT && streamElement instanceof Result) {
            Result result = (Result) streamElement;
            if (DialbackElement.DialbackType.valid == DialbackElement.DialbackType.valueOf(result.getType())) {
                session.dialback(State.TRUSTED);
                return DialbackNegotiationResult.RESTART;
            }
        }

        if (streamElement instanceof Verify) {
            Verify verify = (Verify) streamElement;
            IncomingSession incomingSession = null;
            for (Set<IncomingSession> incomingSessions : getServer().getIncoming().values()) {
                for (IncomingSession item : incomingSessions) {
                    if (item.getDomain().equals(verify.getFrom().getDomain())) {
                        incomingSession = item;
                        break;
                    }
                }
                if (incomingSession != null) {
                    break;
                }
            }
            if (incomingSession != null) {
                incomingSession.send(new Result(verify.getId(), verify.getFrom(), verify.getTo(), null, verify.getType()));
            }
        }

        return DialbackNegotiationResult.FAILED;
    }

    /**
     * Negotiate with the incoming connection.
     *
     * @param connection    incoming connection.
     * @param streamElement response from the remote server.
     * @return {@code true} if connection is verified and trusted else {@code false}.
     */
    public DialbackNegotiationResult negotiateIncoming(TcpBinding connection, Object streamElement) {
        if (streamElement instanceof Result) {
            Result result = (Result) streamElement;
            String id = result.getId() != null ? result.getId() : UUID.randomUUID().toString();
            try {
                getServer().sendWithoutDialback(result.getTo(), new Verify(id, result.getFrom(), result.getTo(), result.getText(), null));
            } catch (Exception e) {
                LOGGER.error("Unable to send message", e);
                return DialbackNegotiationResult.FAILED;
            }
            return DialbackNegotiationResult.IN_PROCESS;
        }

        if (streamElement instanceof Verify) {
            Verify verify = (Verify) streamElement;
            Set<OutgoingSession> outgoingSessions = getServer().getOutgoing().get(verify.getFrom().getDomain());
            if (outgoingSessions == null) {
                sendVerify(connection, verify, DialbackElement.DialbackType.invalid);
                return DialbackNegotiationResult.FAILED;
            }
            OutgoingSession outgoingSession = null;
            for (OutgoingSession item : outgoingSessions) {
                if (State.SENT == item.dialback() && keyCache.get(item.getConnection().getStreamId()) != null) {
                    outgoingSession = item;
                    break;
                }
            }
            if (outgoingSession == null) {
                sendVerify(connection, verify, DialbackElement.DialbackType.invalid);
                return DialbackNegotiationResult.FAILED;
            }
            String streamId = outgoingSession.getConnection().getStreamId();
            DialbackElement.DialbackType type = verify(streamId, verify.getText())
                ? DialbackElement.DialbackType.valid
                : DialbackElement.DialbackType.invalid;
            keyCache.remove(streamId);
            sendVerify(connection, verify, type);
            return type == DialbackElement.DialbackType.valid ? DialbackNegotiationResult.IN_PROCESS : DialbackNegotiationResult.FAILED;
        }

        return DialbackNegotiationResult.IGNORED;
    }

    protected void sendVerify(TcpBinding connection, Verify verify, DialbackElement.DialbackType type) {
        connection.send(
            new Verify(
                verify.getId(),
                verify.getFrom(),
                verify.getTo(),
                null,
                type.name()
            )
        );
    }

    /**
     * Dialback states.
     */
    public enum State {

        /**
         * Domain was verified and trusted.
         */
        TRUSTED,

        /**
         * Dialback key was sending to the Receiving server.
         */
        SENT,

        /**
         * Domain doesn't use dialback.
         */
        DISABLED,

        /**
         * Remote server support server dialback.
         */
        SUPPORT
    }
}
