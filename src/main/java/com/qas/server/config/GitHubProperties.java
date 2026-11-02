package com.qas.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.github")
public record GitHubProperties(String clientId, String clientSecret, String appId, String slug) {
    public boolean configured() {
        return present(clientId) && present(clientSecret) && present(appId)
                && appId.matches("[1-9][0-9]*") && present(slug) && slug.matches("[a-zA-Z0-9-]+");
    }
    private boolean present(String value) { return value != null && !value.isBlank(); }
}
