package com.qas.server.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.*;

class GitHubInstallationStateServiceTest {
    @Test void stateIsBoundToSessionAndSingleUse() {
        var service = new GitHubInstallationStateService(); var session = new MockHttpSession();
        var state = service.issue(session);
        assertFalse(service.consume(new MockHttpSession(), state));
        assertTrue(service.consume(session, state));
        assertFalse(service.consume(session, state));
    }
    @Test void expiredStateFailsClosed() {
        var service = new GitHubInstallationStateService(); var session = new MockHttpSession();
        session.setAttribute(GitHubInstallationStateService.class.getName(),
                new GitHubInstallationStateService.Pending("expired", java.time.Instant.now().minusSeconds(601)));
        assertFalse(service.consume(session, "expired"));
    }
    @Test void missingAndMismatchedStateFailClosed() {
        var service = new GitHubInstallationStateService(); var session = new MockHttpSession();
        service.issue(session);
        assertFalse(service.consume(session, null));
        assertFalse(service.consume(null, "anything"));
    }
}
