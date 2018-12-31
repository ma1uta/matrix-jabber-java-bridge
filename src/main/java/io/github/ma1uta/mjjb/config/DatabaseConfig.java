package io.github.ma1uta.mjjb.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Database configuration.
 */
public class DatabaseConfig {

    @JsonProperty("driver_class")
    private String driverClass = "org.postgresql.Driver";

    private String url = "jdbc:postgresql://localhost/mjjb";

    private String username = "mjjb";

    private String password = "mjjb";

    private Map<String, String> properties;

    public String getDriverClass() {
        return driverClass;
    }

    public void setDriverClass(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
}
