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

package io.github.ma1uta.mjjb.xmpp.netty;

import rocks.xmpp.core.stream.model.StreamElement;
import rocks.xmpp.core.stream.server.ServerStreamFeaturesManager;
import rocks.xmpp.nio.netty.net.NettyChannelConnection;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * XMPP S2S session.
 */
public class Session implements AutoCloseable {

    private NettyChannelConnection connection;
    private final Unmarshaller unmarshaller;
    private final Marshaller marshaller;
    private final ServerStreamFeaturesManager streamFeaturesManager = new ServerStreamFeaturesManager();

    public Session() throws JAXBException {
        unmarshaller = ServerConfiguration.JAXB_CONTEXT.createUnmarshaller();
        marshaller = ServerConfiguration.JAXB_CONTEXT.createMarshaller();
    }

    public Unmarshaller getUnmarshaller() {
        return unmarshaller;
    }

    public Marshaller getMarshaller() {
        return marshaller;
    }

    /**
     * Handle stream (open, close, restart).
     *
     * @param streamElement stream stanza.
     * @return {@code true} to restart stream, else {@code false}.
     */
    public boolean handleStream(Object streamElement) {
        return false;
    }

    /**
     * Read handler.
     *
     * @param xml     string representation of the stanza.
     * @param element stanza.
     */
    public void onRead(String xml, StreamElement element) {

    }

    /**
     * Write handler.
     *
     * @param xml     string representation of the stanza.
     * @param element stanza.
     */
    public void onWrite(String xml, StreamElement element) {

    }

    /**
     * Exception handler.
     *
     * @param throwable exception.
     */
    public void onException(Throwable throwable) {

    }

    public ServerStreamFeaturesManager getStreamFeaturesManager() {
        return streamFeaturesManager;
    }

    public NettyChannelConnection getConnection() {
        return connection;
    }

    public void setConnection(NettyChannelConnection connection) {
        this.connection = connection;
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
