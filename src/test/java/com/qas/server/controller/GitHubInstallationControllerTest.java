package com.qas.server.controller;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.qas.server.client.GitHubApiClient;
import com.qas.server.service.GitHubInstallationStateService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties={"app.github.client-id=test-client", "app.github.client-secret=test-secret", "app.github.app-id=42", "app.github.slug=qa-test", "app.frontend.base-url=http://localhost:3000"})
@AutoConfigureMockMvc
class GitHubInstallationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired GitHubInstallationStateService states;
    @MockitoBean GitHubApiClient github;

    @Test void configuredFrontendCanSendCredentialedApiRequests() throws Exception {
        mvc.perform(options("/api/github/installations/start")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-CSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test void untrustedFrontendCannotReadApiResponses() throws Exception {
        mvc.perform(get("/api/config").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void anonymousCanReadConfigButNotPrivateApis() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.githubConfigured").value(true));
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/github/installations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/github/installations/start").with(csrf())).andExpect(status().isUnauthorized());
    }
    @Test void loginUsesAppClientAndPkce() throws Exception {
        mvc.perform(get("/oauth2/authorization/github"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(containsString("client_id=test-client"),
                        containsString("state="), containsString("code_challenge="), containsString("code_challenge_method=S256"),
                        containsString("redirect_uri=http://localhost/login/oauth2/code/github"))));
    }
    @Test void installAndLogoutRequireCsrf() throws Exception {
        mvc.perform(post("/api/github/installations/start").with(oauth2Login())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").with(oauth2Login())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").with(oauth2Login()).with(csrf())).andExpect(status().isNoContent());
    }
    @Test void profileReturnsOnlyPublicFields() throws Exception {
        mvc.perform(get("/api/me").with(oauth2Login().attributes(a -> {
            a.put("id", 1); a.put("login", "octocat"); a.put("email", "private@example.com");
        }))).andExpect(status().isOk()).andExpect(jsonPath("$.login").value("octocat"))
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.accessToken").doesNotExist());
    }
    @Test void installStartsWithServerGeneratedState() throws Exception {
        mvc.perform(post("/api/github/installations/start").with(oauth2Login()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url", startsWith("https://github.com/apps/qa-test/installations/new?state=")));
    }
    @Test void callbackVerifiesInstallationAndRejectsReplay() throws Exception {
        var session = new MockHttpSession(); var state = states.issue(session);
        var installation = Map.of("id", 101L, "app_id", 42,
                "account", Map.of("login", "octocat", "type", "User"), "repository_selection", "selected");
        when(github.fetchInstallationsPage(eq(1), any()))
                .thenReturn(Map.of("installations", List.of(installation)));
        mvc.perform(get("/api/github/installations/callback").session(session).with(oauth2Login())
                        .param("state", state).param("installation_id", "101").param("setup_action", "install"))
                .andExpect(redirectedUrl("http://localhost:3000/?installation=success"));
        mvc.perform(get("/api/github/installations/callback").session(session).with(oauth2Login())
                        .param("state", state).param("installation_id", "101").param("setup_action", "install"))
                .andExpect(redirectedUrl("http://localhost:3000/?installation=invalid-state"));
        verify(github, times(1)).fetchInstallationsPage(eq(1), any());
    }
    @Test void cannotClaimAnotherUsersInstallation() throws Exception {
        var session = new MockHttpSession(); var state = states.issue(session);
        when(github.fetchInstallationsPage(eq(1), any())).thenReturn(Map.of("installations", List.of()));
        mvc.perform(get("/api/github/installations/callback").session(session).with(oauth2Login())
                        .param("state", state).param("installation_id", "999").param("setup_action", "install"))
                .andExpect(redirectedUrl("http://localhost:3000/?installation=unverified"));
    }
    @Test void pendingOrganizationApprovalIsNotSuccess() throws Exception {
        var session = new MockHttpSession(); var state = states.issue(session);
        mvc.perform(get("/api/github/installations/callback").session(session).with(oauth2Login())
                        .param("state", state).param("setup_action", "request"))
                .andExpect(redirectedUrl("http://localhost:3000/?installation=pending"));
        verifyNoInteractions(github);
    }
}
