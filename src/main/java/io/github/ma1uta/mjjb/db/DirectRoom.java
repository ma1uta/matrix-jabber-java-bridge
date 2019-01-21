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

package io.github.ma1uta.mjjb.db;

import rocks.xmpp.addr.Jid;

/**
 * One-to-one room.
 */
public class DirectRoom {

    private String matrixUser;

    private String xmppUser;

    private Jid xmppJid;

    private String roomId;

    private boolean matrixSubscribed;

    private boolean xmppSubscribed;

    public String getMatrixUser() {
        return matrixUser;
    }

    public void setMatrixUser(String matrixUser) {
        this.matrixUser = matrixUser;
    }

    public String getXmppUser() {
        return xmppUser;
    }

    public void setXmppUser(String xmppUser) {
        this.xmppUser = xmppUser;
    }

    /**
     * Get JID.
     *
     * @return JID.
     */
    public Jid getXmppJid() {
        if (xmppJid == null) {
            xmppJid = Jid.of(xmppUser);
        }
        return xmppJid;
    }

    /**
     * Set JID.
     *
     * @param xmppJid JID.
     */
    public void setXmppJid(Jid xmppJid) {
        this.xmppJid = xmppJid;
        this.xmppUser = xmppJid.toString();
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public boolean isMatrixSubscribed() {
        return matrixSubscribed;
    }

    public void setMatrixSubscribed(boolean matrixSubscribed) {
        this.matrixSubscribed = matrixSubscribed;
    }

    public boolean isXmppSubscribed() {
        return xmppSubscribed;
    }

    public void setXmppSubscribed(boolean xmppSubscribed) {
        this.xmppSubscribed = xmppSubscribed;
    }
}
