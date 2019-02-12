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

import rocks.xmpp.addr.Jid;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Dialback key.
 */
@XmlRootElement(name = "result", namespace = ServerDialback.NAMESPACE)
public class Result extends DialbackElement {

    public Result() {
        super(null, null, null, null, null);
    }

    public Result(String id, Jid to, Jid from, String key, String type) {
        super(id, to, from, key, type);
    }
}

