package com.qas.server.controller;

import java.net.URI;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.qas.server.dto.GitHubInstallationResponse;
import com.qas.server.dto.GitHubInstallationStartResponse;
import com.qas.server.dto.GitHubRepositoryPageResponse;
import com.qas.server.service.GitHubInstallationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/github/installations")
public class GitHubInstallationController {
    private final GitHubInstallationService installationService;

    public GitHubInstallationController(GitHubInstallationService installationService) {
        this.installationService = installationService;
    }

    @GetMapping
    public List<GitHubInstallationResponse> getInstallations(Authentication authentication) {
        return installationService.getInstallations(authentication);
    }

    @GetMapping("/{id}/repositories")
    public GitHubRepositoryPageResponse getRepositories(@PathVariable long id,
            @RequestParam(defaultValue = "1") int page, Authentication authentication) {
        return installationService.getRepositories(id, page, authentication);
    }

    @PostMapping("/start")
    public GitHubInstallationStartResponse startInstallation(HttpSession session) {
        return new GitHubInstallationStartResponse(installationService.createInstallationUrl(session));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> completeInstallation(@RequestParam(required = false) String state,
            @RequestParam(name = "installation_id", required = false) Long installationId,
            @RequestParam(name = "setup_action", required = false) String setupAction,
            HttpServletRequest request, Authentication authentication) {
        String redirectUrl = installationService.completeInstallation(
                state, installationId, setupAction, request.getSession(false), authentication);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }
}
