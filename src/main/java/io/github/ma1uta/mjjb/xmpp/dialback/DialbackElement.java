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
import rocks.xmpp.core.stream.model.StreamElement;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;

/**
 * Common dialback stream element.
 */
@XmlTransient
public abstract class DialbackElement implements StreamElement {

    protected DialbackElement(Jid to, Jid from, String text, DialbackType type) {
        this.to = to;
        this.from = from;
        this.text = text;
        this.type = type;
    }

    @XmlAttribute
    private String id;

    @XmlAttribute
    private Jid to;

    @XmlAttribute
    private Jid from;

    @XmlAttribute
    private DialbackType type;

    @XmlValue
    private String text;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Jid getTo() {
        return to;
    }

    public void setTo(Jid to) {
        this.to = to;
    }

    public Jid getFrom() {
        return from;
    }

    public void setFrom(Jid from) {
        this.from = from;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public DialbackType getType() {
        return type;
    }

    public void setType(DialbackType type) {
        this.type = type;
    }


    /**
     * Dialback type enumeration.
     */
    @XmlEnum
    public enum DialbackType {

        /**
         * Valid.
         */
        valid,

        /**
         * Invalid.
         */
        invalid,

        /**
         * Error.
         */
        error
    }

}
