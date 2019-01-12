package io.github.ma1uta.mjjb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.ma1uta.mjjb.config.AppConfig;
import io.github.ma1uta.mjjb.config.DatabaseConfig;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.config.XmppConfig;
import io.github.ma1uta.mjjb.matrix.MatrixServer;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import org.jdbi.v3.core.Jdbi;

/**
 * Matrix-XMPP bridge.
 */
public class Bridge {

    private MatrixServer matrixServer;
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

        RouterFactory routerFactory = initRouters(config);

        initMatrix(config.getMatrix(), routerFactory);
        initXmpp(config.getXmpp(), routerFactory);

        this.matrixServer.run();
        this.xmppServer.run();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                this.matrixServer.close();
                this.xmppServer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            dataSource.close();
        }));
    }

    private RouterFactory initRouters(AppConfig config) {
        return new RouterFactory(config, jdbi);
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

    private void initMatrix(MatrixConfig config, RouterFactory routerFactory) {
        this.matrixServer = new MatrixServer();
        this.matrixServer.init(jdbi, config, routerFactory);
    }

    private void initXmpp(XmppConfig config, RouterFactory routerFactory) {
        this.xmppServer = new XmppServer();
        this.xmppServer.init(jdbi, config, routerFactory);
    }
}
