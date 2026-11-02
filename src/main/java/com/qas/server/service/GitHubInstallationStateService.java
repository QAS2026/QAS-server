package com.qas.server.service;

import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class GitHubInstallationStateService {
    private static final String SESSION_KEY = GitHubInstallationStateService.class.getName();
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(HttpSession session) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_KEY, new Pending(state, Instant.now()));
        return state;
    }

    public boolean consume(HttpSession session, String state) {
        if (session == null) {
            return false;
        }
        synchronized (session) {
            Object attribute = session.getAttribute(SESSION_KEY);
            session.removeAttribute(SESSION_KEY);
            return attribute instanceof Pending pending
                    && pending.value().equals(state)
                    && pending.createdAt().plus(STATE_TTL).isAfter(Instant.now());
        }
    }

    record Pending(String value, Instant createdAt) implements Serializable {}
}
