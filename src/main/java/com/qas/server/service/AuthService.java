package com.qas.server.service;

import java.util.Objects;
import com.qas.server.config.GitHubProperties;
import com.qas.server.config.FrontendProperties;
import com.qas.server.dto.GitHubUserResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final GitHubProperties gitHubProperties;

    private final FrontendProperties frontendProperties;

    public AuthService(GitHubProperties gitHubProperties, FrontendProperties frontendProperties) {
        this.gitHubProperties = gitHubProperties;
        this.frontendProperties = frontendProperties;
    }

    public String getLoginRedirectUrl() {
        return gitHubProperties.configured() ? "/oauth2/authorization/github" : frontendProperties.redirectToHome("authError=configuration");
    }

    public GitHubUserResponse getCurrentUser(Authentication authentication) {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        return new GitHubUserResponse(user.getAttribute("id"), user.getAttribute("login"),
                Objects.toString(user.getAttribute("name"), user.getAttribute("login")),
                Objects.toString(user.getAttribute("avatar_url"), ""));
    }
}
