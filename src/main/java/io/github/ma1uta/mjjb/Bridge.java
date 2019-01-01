package io.github.ma1uta.mjjb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.ma1uta.mjjb.config.AppConfig;
import io.github.ma1uta.mjjb.config.Cert;
import io.github.ma1uta.mjjb.config.DatabaseConfig;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.config.XmppConfig;
import io.github.ma1uta.mjjb.matrix.MatrixEndPoints;
import io.github.ma1uta.mjjb.matrix.netty.JerseyServerInitializer;
import io.github.ma1uta.mjjb.matrix.netty.NettyHttpContainer;
import io.github.ma1uta.mjjb.netty.NettyBuilder;
import io.github.ma1uta.mjjb.xmpp.netty.XmppServer;
import io.github.ma1uta.mjjb.xmpp.netty.XmppServerInitializer;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import org.jdbi.v3.core.Jdbi;

import java.net.URI;
import javax.net.ssl.SSLContext;

/**
 * Matrix-XMPP bridge.
 */
public class Bridge {

    private Channel matrixChannel;
    private Channel xmppChannel;
    private XmppServer xmppServer;

    private HikariDataSource dataSource;

    private Jdbi jdbi;

    /**
     * Run bridge with the specified configuration.
     *
     * @param config bridge configuration.
     */
    public void run(AppConfig config) {
        initDatabase(config.getDatabase());

        initMatrix(config.getMatrix());
        initXmpp(config.getXmpp());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                matrixChannel.close().sync();
                xmppChannel.close().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            dataSource.close();
        }));
    }

    private void initDatabase(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(config.getDriverClass());
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        config.getProperties().forEach(hikariConfig::addDataSourceProperty);

        dataSource = new HikariDataSource(hikariConfig);
        jdbi = Jdbi.create(dataSource);
    }

    private void initMatrix(MatrixConfig config) {
        SslContext nettyContext = null;
        Cert cert = config.getSsl();
        if (cert != null) {
            try {
                nettyContext = cert.createNettyContext();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        URI uri = URI.create(config.getUrl());

        NettyHttpContainer container = new NettyHttpContainer(new MatrixEndPoints(this.jdbi, config));
        JerseyServerInitializer initializer = new JerseyServerInitializer(uri, nettyContext, container);
        matrixChannel = NettyBuilder
            .createServer(uri.getHost(), NettyBuilder.getPort(uri), initializer, f -> container.getApplicationHandler().onShutdown(container));
    }

    private void initXmpp(XmppConfig config) {
        SSLContext javaContext = null;
        Cert cert = config.getSsl();
        if (cert != null) {
            try {
                javaContext = cert.createJavaContext();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        this.xmppServer = new XmppServer(config, javaContext);
        XmppServerInitializer initializer = new XmppServerInitializer(this.xmppServer);
        xmppChannel = NettyBuilder
            .createServer(config.getDomain(), config.getPort(), initializer, f -> this.xmppServer.getInitialIncomingSessions().forEach(s -> {
                try {
                    s.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
    }
}
