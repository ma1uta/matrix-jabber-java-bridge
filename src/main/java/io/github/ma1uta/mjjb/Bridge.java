package io.github.ma1uta.mjjb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.ma1uta.mjjb.config.AppConfig;
import io.github.ma1uta.mjjb.config.DatabaseConfig;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import io.github.ma1uta.mjjb.config.XmppConfig;
import io.github.ma1uta.mjjb.matrix.MatrixServer;
import io.github.ma1uta.mjjb.xmpp.XmppServer;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.sql.SQLException;

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

        for (AbstractRouter<?> router : routerFactory.getXmppRouters().values()) {
            router.init(jdbi, xmppServer, matrixServer);
        }
        for (AbstractRouter<?> router : routerFactory.getMatrixRouters().values()) {
            router.init(jdbi, xmppServer, matrixServer);
        }

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
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.installPlugin(new PostgresPlugin());
        updateSchema();
    }

    private void updateSchema() {
        try {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(dataSource.getConnection()));
            Liquibase liquibase = new Liquibase(getClass().getResource("/migrations.xml").getFile(), new FileSystemResourceAccessor(),
                database);
            liquibase.update(new Contexts(), new LabelExpression());
        } catch (SQLException | LiquibaseException e) {
            e.printStackTrace();
        }
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
