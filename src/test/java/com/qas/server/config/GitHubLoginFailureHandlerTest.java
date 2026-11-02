package com.qas.server.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.core.*;
import static org.junit.jupiter.api.Assertions.*;

class GitHubLoginFailureHandlerTest {
    @ParameterizedTest
    @CsvSource({"incorrect_client_credentials,credentials", "invalid_client,credentials", "redirect_uri_mismatch,redirect",
            "authorization_request_not_found,session", "invalid_state_parameter,session", "bad_verification_code,code",
            "unverified_user_email,email", "access_denied,cancelled", "invalid_token_response,token", "invalid_user_info_response,profile"})
    void emitsOnlySafeFailureCategory(String code, String expected) throws Exception {
        var exception = new OAuth2AuthenticationException(new OAuth2Error(code, "private-token-do-not-echo", null));
        var response = new MockHttpServletResponse();
        new GitHubLoginFailureHandler(new FrontendProperties("http://localhost:3000")).onAuthenticationFailure(new MockHttpServletRequest(), response, exception);
        assertEquals("http://localhost:3000/?authError=" + expected, response.getRedirectedUrl());
        assertFalse(response.getContentAsString().contains("private-token"));
    }
    @Test void extractsNestedGithubErrorWithoutExposingResponse() {
        var exception = new OAuth2AuthenticationException(new OAuth2Error("invalid_token_response"),
                "Token failed: [incorrect_client_credentials] private-response");
        assertEquals("credentials", GitHubLoginFailureHandler.category(exception));
    }
}
