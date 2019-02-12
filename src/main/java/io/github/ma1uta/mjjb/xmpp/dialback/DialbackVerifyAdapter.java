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

import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * Because NettyChannelConnection from the xmpp.rocks doesn't allow override anything let do something to marshal/unmarshal "db:verify".
 */
public class DialbackVerifyAdapter extends XmlAdapter<String, Verify> {

    public DialbackVerifyAdapter() {
    }

    @Override
    public Verify unmarshal(String v) throws Exception {
        throw new UnsupportedOperationException("Unable to unmarshal db:verify");
    }

    @Override
    public String marshal(Verify v) throws Exception {
        StringBuilder builder = new StringBuilder("<db:verify id=\"")
            .append(v.getId())
            .append("\" from=\"")
            .append(v.getFrom())
            .append("\" to=\"")
            .append(v.getTo())
            .append("\"");
        if (v.getType() != null) {
            builder.append(" type=\"").append(v.getType()).append("\"");
        }
        builder.append(">");
        if (v.getText() != null) {
            builder.append(v.getText());
        }
        return builder.append("</db:verify>").toString();
    }
}
