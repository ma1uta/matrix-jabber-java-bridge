package io.github.ma1uta.mjjb.config;

/**
 * Main appservice configuration.
 */
public class AppConfig {

    private MatrixConfig matrix;

    private XmppConfig xmpp;

    private DatabaseConfig database;

    public MatrixConfig getMatrix() {
        return matrix;
    }

    public void setMatrix(MatrixConfig matrix) {
        this.matrix = matrix;
    }

    public XmppConfig getXmpp() {
        return xmpp;
    }

    public void setXmpp(XmppConfig xmpp) {
        this.xmpp = xmpp;
    }

    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }
}
