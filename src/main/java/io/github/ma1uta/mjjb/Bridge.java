package io.github.ma1uta.mjjb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.ma1uta.mjjb.config.AppConfig;
import io.github.ma1uta.mjjb.config.DatabaseConfig;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.matrix.netty.JerseyServerInitializer;
import io.github.ma1uta.mjjb.matrix.MatrixEndPoints;
import io.github.ma1uta.mjjb.matrix.netty.NettyHttpContainer;
import io.github.ma1uta.mjjb.netty.NettyBuilder;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContextBuilder;
import org.jdbi.v3.core.Jdbi;

import java.net.URI;
import javax.ws.rs.core.UriBuilder;

/**
 * Matrix-XMPP bridge.
 */
public class Bridge {

    private Channel matrixChannel;

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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                matrixChannel.close().sync();
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
        URI uri = URI.create(config.getUrl());

        NettyHttpContainer container = new NettyHttpContainer(new MatrixEndPoints(this.jdbi, config));
        JerseyServerInitializer initializer = new JerseyServerInitializer(uri, null, container);
        matrixChannel = NettyBuilder.createServer(uri, initializer, f -> container.getApplicationHandler().onShutdown(container));
    }
}
