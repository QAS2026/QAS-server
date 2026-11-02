package com.qas.server.service;

import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.qas.server.config.GitHubProperties;
import com.qas.server.config.FrontendProperties;
import com.qas.server.client.GitHubApiClient;
import com.qas.server.exception.GitHubIntegrationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitHubInstallationServiceTest {
    GitHubInstallationService installationService;
    MockRestServiceServer server;
    Authentication authentication = mock(Authentication.class);
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://api.github.com");
        server = MockRestServiceServer.bindTo(builder).build();
        var manager = mock(OAuth2AuthorizedClientManager.class);
        var registration = ClientRegistration.withRegistrationId("github").clientId("client").clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/callback")
                .authorizationUri("https://github.com/login/oauth/authorize").tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user").userNameAttributeName("id").build();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "server-only-token", Instant.now(), Instant.now().plusSeconds(3600));
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration, "test-user", token));
        installationService = new GitHubInstallationService(new GitHubApiClient(builder.build(), manager),
                new GitHubProperties("client", "secret", "42", "qa-test"), new GitHubInstallationStateService(), new FrontendProperties("http://localhost:3000"));
    }
    @AfterEach void verifyRequests() { server.verify(); }
    @Test void filtersOtherAppsAndSuspendedInstallations() {
        server.expect(requestTo("https://api.github.com/user/installations?per_page=100&page=1"))
                .andExpect(header("Authorization", "Bearer server-only-token"))
                .andRespond(withSuccess("""
                  {"installations":[
                    {"id":101,"app_id":42,"account":{"login":"octocat","type":"User"},"repository_selection":"selected","suspended_at":null},
                    {"id":102,"app_id":99,"account":{},"suspended_at":null},
                    {"id":103,"app_id":42,"account":{},"suspended_at":"2026-01-01"}]}
                  """, MediaType.APPLICATION_JSON));
        var result = installationService.getInstallations(authentication);
        assertEquals(1, result.size()); assertEquals(101, result.getFirst().id());
    }
    @Test void repositoryAccessRequiresVerifiedInstallation() {
        server.expect(requestTo("https://api.github.com/user/installations?per_page=100&page=1"))
                .andRespond(withSuccess("{\"installations\":[]}", MediaType.APPLICATION_JSON));
        var error = assertThrows(GitHubIntegrationException.class, () -> installationService.getRepositories(999, 1, authentication));
        assertEquals(403, error.getStatus());
    }
    @Test void revokedTokenReturnsReauthenticationWithoutLeakingGitHubResponse() {
        server.expect(anything()).andRespond(withUnauthorizedRequest());
        var error = assertThrows(GitHubIntegrationException.class, () -> installationService.getInstallations(authentication));
        assertEquals(401, error.getStatus()); assertFalse(error.getMessage().contains("server-only-token"));
    }
}
