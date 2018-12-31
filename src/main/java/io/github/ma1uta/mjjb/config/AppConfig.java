package io.github.ma1uta.mjjb.config;

/**
 * Main appservice configuration.
 */
public class AppConfig {

    private MatrixConfig matrix;

    private DatabaseConfig database;

    public MatrixConfig getMatrix() {
        return matrix;
    }

    public void setMatrix(MatrixConfig matrix) {
        this.matrix = matrix;
    }

    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }
}
