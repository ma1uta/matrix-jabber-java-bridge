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

import io.github.ma1uta.mjjb.Loggers;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

/**
 * XMPP SRV name resolver.
 */
public class SrvNameResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(Loggers.LOGGER);

    private DnsNameResolver resolver;

    public SrvNameResolver() {
        this.resolver = new DnsNameResolverBuilder(new NioEventLoopGroup().next())
            .channelFactory(new ReflectiveChannelFactory<>(NioDatagramChannel.class)).build();
    }

    /**
     * Resolve domain address and invoke the action with resolved address and port.
     *
     * @param domain   XMPP domain.
     * @param consumer action to invoke.
     */
    public void resolve(String domain, BiConsumer<String, Integer> consumer) {
        String query = "_xmpp-server._tcp." + domain + ".";
        List<Record> records = new ArrayList<>();
        try {
            List<DnsRecord> srvRecords = resolver.resolveAll(new DefaultDnsQuestion(query, DnsRecordType.SRV)).get();
            for (DnsRecord srvRecord : srvRecords) {
                if (srvRecord instanceof DefaultDnsRawRecord) {
                    DefaultDnsRawRecord rawRecord = (DefaultDnsRawRecord) srvRecord;
                    ByteBuf content = rawRecord.content();
                    int priority = content.readUnsignedShort();
                    int weight = content.readUnsignedShort();
                    int port = content.readUnsignedShort();
                    records.add(new Record(priority, weight, port, extractHostname(content)));
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            if (!(e.getCause() instanceof UnknownHostException)) {
                LOGGER.error(String.format("Unable to resolve SRV record: %s", query), e);
                throw new RuntimeException(e);
            }
        }

        if (records.isEmpty()) {
            records.add(new Record(0, 0, XmppServer.DEFAULT_S2S_PORT, domain));
        } else {
            records.sort((r1, r2) -> r1.getPriority() == r2.getPriority()
                ? Integer.compare(r1.getWeight(), r2.getWeight())
                : Integer.compare(r1.getPriority(), r2.getPriority()));
        }
        Exception lastException = null;
        for (Record record : records) {
            try {
                consumer.accept(record.getHostname(), record.getPort());
                lastException = null;
                break;
            } catch (Exception e) {
                LOGGER.error(String.format("Unable to connect to the %s:%d", record.getHostname(), record.getPort()), e);
                lastException = e;
            }
        }
        if (lastException != null) {
            LOGGER.error(String.format("Unable to connect to the \"%s\".", domain), lastException);
            throw new RuntimeException(lastException);
        }
    }

    private String extractHostname(ByteBuf content) {
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);
        char[] chars = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            chars[i] = Character.isAlphabetic(bytes[i]) || Character.isDigit(bytes[i]) ? (char) bytes[i] : '.';
        }
        String hostname = new String(chars);
        if (hostname.startsWith(".")) {
            hostname = hostname.substring(1);
        }
        if (hostname.endsWith(".")) {
            hostname = hostname.substring(0, hostname.length() - 1);
        }
        return hostname;
    }

    private static class Record {
        private int priority;
        private int weight;
        private int port;
        private String hostname;

        Record(int priority, int weight, int port, String hostname) {
            this.priority = priority;
            this.weight = weight;
            this.port = port;
            this.hostname = hostname;
        }

        int getPriority() {
            return priority;
        }

        int getWeight() {
            return weight;
        }

        int getPort() {
            return port;
        }

        String getHostname() {
            return hostname;
        }
    }
}
