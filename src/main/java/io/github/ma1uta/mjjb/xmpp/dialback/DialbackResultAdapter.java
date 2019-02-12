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
 * Because NettyChannelConnection from the xmpp.rocks doesn't allow override anything let do something to marshal/unmarshal "db:result".
 */
public class DialbackResultAdapter extends XmlAdapter<String, Result> {

    public DialbackResultAdapter() {
    }

    @Override
    public Result unmarshal(String v) throws Exception {
        throw new UnsupportedOperationException("Unable to unmarshal db:result.");
    }

    @Override
    public String marshal(Result v) throws Exception {
        StringBuilder builder = new StringBuilder("<db:result id=\"")
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
        return builder.append("</db:result>").toString();
    }
}
