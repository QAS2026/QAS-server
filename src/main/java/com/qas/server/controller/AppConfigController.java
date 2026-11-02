package com.qas.server.controller;

import com.qas.server.config.GitHubProperties;
import com.qas.server.dto.AppConfigResponse;
import com.qas.server.dto.CsrfTokenResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppConfigController {
    private final GitHubProperties gitHubProperties;

    public AppConfigController(GitHubProperties gitHubProperties) {
        this.gitHubProperties = gitHubProperties;
    }

    @GetMapping("/api/config")
    public AppConfigResponse getConfig() {
        return new AppConfigResponse(gitHubProperties.configured());
    }

    @GetMapping("/api/csrf")
    public CsrfTokenResponse getCsrfToken(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }
}
