package com.qas.server.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.frontend")
public record FrontendProperties(String baseUrl) {
    public FrontendProperties {
        URI uri = URI.create(baseUrl);
        boolean validScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        boolean rootPath = uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath());
        if (!validScheme || uri.getHost() == null || uri.getRawUserInfo() != null || !rootPath
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Frontend base URL must be an HTTP(S) origin without a path, query or fragment.");
        }
        baseUrl = uri.getScheme() + "://" + uri.getRawAuthority();
    }

    public String homeUrl() {
        return baseUrl + "/";
    }

    public String redirectToHome(String query) {
        return homeUrl() + "?" + query;
    }
}
