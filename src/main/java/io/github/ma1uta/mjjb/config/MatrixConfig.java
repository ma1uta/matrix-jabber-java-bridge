package io.github.ma1uta.mjjb.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Matrix configuration.
 */
public class MatrixConfig {

    private String url;

    @JsonProperty("as_token")
    private String asToken;

    @JsonProperty("hs_token")
    private String hsToken;

    @JsonProperty("master_user_id")
    private String masterUserId;

    private String prefix;

    private Cert ssl;

    private String homeserver;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAsToken() {
        return asToken;
    }

    public void setAsToken(String asToken) {
        this.asToken = asToken;
    }

    public String getHsToken() {
        return hsToken;
    }

    public void setHsToken(String hsToken) {
        this.hsToken = hsToken;
    }

    public String getMasterUserId() {
        return masterUserId;
    }

    public void setMasterUserId(String masterUserId) {
        this.masterUserId = masterUserId;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Cert getSsl() {
        return ssl;
    }

    public void setSsl(Cert ssl) {
        this.ssl = ssl;
    }

    public String getHomeserver() {
        return homeserver;
    }

    public void setHomeserver(String homeserver) {
        this.homeserver = homeserver;
    }
}
