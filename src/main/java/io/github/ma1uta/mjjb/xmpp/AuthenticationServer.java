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

package io.github.ma1uta.mjjb.xmpp;

import java.util.Map;

/**
 * Server to authenticate remote xmpp servers.
 */
public class AuthenticationServer {

    /**
     * Session statuses.
     */
    public enum Status {

        /**
         * Manually trusted.
         */
        TRUSTED,

        /**
         * Manually untrusted.
         */
        UNTRUSTED,

        /**
         * Authenticated was success.
         */
        VALID,

        /**
         * Authentication was failed.
         */
        INVALID,

        /**
         * Authentication in process.
         */
        IN_PROCESS
    }

    private Map<OutgoingSession, Status> sessions;

    /**
     * Authenticate the outgoing session.
     *
     * @param session the outgoing session.
     */
    public void authenticate(OutgoingSession session) {
        Status status = sessions.get(session);

        if (status == null) {
            sessions.put(session, Status.IN_PROCESS);
            return;
        }

        switch (status) {
            case INVALID:
            case UNTRUSTED:
                throw new SecurityException(String.format("The session to the \"%s\" cannout authenticate", session.getDomain()));
            case IN_PROCESS:
                throw new IllegalStateException(String.format("Unable to authenticate the session to the \"%s\"", session.getDomain()));
            case TRUSTED:
            case VALID:
            default:
                // nothing to do
        }
    }

    /**
     * Close the specified session.
     *
     * @param session session to close.
     */
    public void close(OutgoingSession session) {
        sessions.remove(session);
    }
}
