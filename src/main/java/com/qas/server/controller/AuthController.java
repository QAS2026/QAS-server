package com.qas.server.controller;

import java.net.URI;
import com.qas.server.dto.GitHubUserResponse;
import com.qas.server.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/api/auth/login")
    public ResponseEntity<Void> login() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authService.getLoginRedirectUrl()))
                .build();
    }

    @GetMapping("/api/me")
    public GitHubUserResponse getCurrentUser(Authentication authentication) {
        return authService.getCurrentUser(authentication);
    }
}
