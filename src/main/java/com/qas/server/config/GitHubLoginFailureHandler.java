package com.qas.server.config;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class GitHubLoginFailureHandler implements AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(GitHubLoginFailureHandler.class);
    private static final Map<String, String> CATEGORIES = Map.ofEntries(
            Map.entry("incorrect_client_credentials", "credentials"),
            Map.entry("invalid_client", "credentials"),
            Map.entry("redirect_uri_mismatch", "redirect"),
            Map.entry("authorization_request_not_found", "session"),
            Map.entry("invalid_state_parameter", "session"),
            Map.entry("bad_verification_code", "code"),
            Map.entry("unverified_user_email", "email"),
            Map.entry("access_denied", "cancelled"));

    private final FrontendProperties frontendProperties;

    public GitHubLoginFailureHandler(FrontendProperties frontendProperties) {
        this.frontendProperties = frontendProperties;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        String category = category(exception);
        // Log only a fixed category. Exception messages may include OAuth codes or token responses.
        log.warn("GitHub login failed: category={}", category);
        response.sendRedirect(frontendProperties.redirectToHome("authError=" + category));
    }

    static String category(AuthenticationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            for (var entry : CATEGORIES.entrySet()) {
                if (cause instanceof OAuth2AuthenticationException oauth
                        && entry.getKey().equals(oauth.getError().getErrorCode())) return entry.getValue();
                if (message != null && message.contains("[" + entry.getKey() + "]")) return entry.getValue();
            }
        }
        if (exception instanceof OAuth2AuthenticationException oauth) {
            return switch (oauth.getError().getErrorCode()) {
                case "invalid_token_response" -> "token";
                case "invalid_user_info_response", "missing_user_info_uri", "missing_user_name_attribute" -> "profile";
                default -> "login";
            };
        }
        return "login";
    }
}
