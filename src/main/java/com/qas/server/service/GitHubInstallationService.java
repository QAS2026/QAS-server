package com.qas.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import com.qas.server.client.GitHubApiClient;
import com.qas.server.config.GitHubProperties;
import com.qas.server.config.FrontendProperties;
import com.qas.server.dto.GitHubInstallationResponse;
import com.qas.server.dto.GitHubRepositoryResponse;
import com.qas.server.dto.GitHubRepositoryPageResponse;
import com.qas.server.exception.GitHubIntegrationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class GitHubInstallationService {
    private static final int INSTALLATIONS_PER_PAGE = 100;
    private static final int MAX_INSTALLATION_PAGES = 100;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubProperties gitHubProperties;
    private final GitHubInstallationStateService installationStateService;

    private final FrontendProperties frontendProperties;

    public GitHubInstallationService(GitHubApiClient gitHubApiClient, GitHubProperties gitHubProperties,
            GitHubInstallationStateService installationStateService, FrontendProperties frontendProperties) {
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubProperties = gitHubProperties;
        this.installationStateService = installationStateService;
        this.frontendProperties = frontendProperties;
    }

    public List<GitHubInstallationResponse> getInstallations(Authentication authentication) {
        List<GitHubInstallationResponse> installations = new ArrayList<>();
        for (int page = 1; page <= MAX_INSTALLATION_PAGES; page++) {
            var response = gitHubApiClient.fetchInstallationsPage(page, authentication);
            var rows = getRows(response, "installations");
            for (var row : rows) {
                if (!isActiveInstallationForCurrentApp(row)) {
                    continue;
                }
                var account = getObject(row.get("account"));
                installations.add(new GitHubInstallationResponse(((Number) row.get("id")).longValue(),
                        String.valueOf(account.get("login")), String.valueOf(account.get("type")),
                        String.valueOf(row.get("repository_selection"))));
            }
            if (rows.size() < INSTALLATIONS_PER_PAGE) {
                return installations;
            }
        }
        throw new GitHubIntegrationException(502, "설치 목록이 너무 큽니다. 조회 범위를 확인해 주세요.");
    }

    public GitHubRepositoryPageResponse getRepositories(long installationId, int page, Authentication authentication) {
        if (installationId <= 0 || page < 1 || page > 10000) {
            throw new GitHubIntegrationException(400, "올바른 설치 ID와 페이지를 입력해 주세요.");
        }
        if (!canAccessInstallation(installationId, authentication)) {
            throw new GitHubIntegrationException(403, "이 설치에 접근할 권한이 없습니다.");
        }

        var response = gitHubApiClient.fetchRepositoriesPage(installationId, page, authentication);
        var repositories = getRows(response, "repositories").stream().map(row -> new GitHubRepositoryResponse(
                ((Number) row.get("id")).longValue(), String.valueOf(row.get("full_name")),
                Boolean.TRUE.equals(row.get("private")), String.valueOf(row.get("html_url")))).toList();
        return new GitHubRepositoryPageResponse(((Number) response.get("total_count")).intValue(), page, repositories);
    }

    public String createInstallationUrl(HttpSession session) {
        if (!gitHubProperties.configured()) {
            throw new GitHubIntegrationException(503, "GitHub App 설정이 필요합니다.");
        }
        String state = installationStateService.issue(session);
        return "https://github.com/apps/" + gitHubProperties.slug() + "/installations/new?state=" + state;
    }

    public String completeInstallation(String state, Long installationId, String setupAction,
            HttpSession session, Authentication authentication) {
        if (!installationStateService.consume(session, state)) {
            return frontendProperties.redirectToHome("installation=invalid-state");
        }
        if ("request".equals(setupAction)) {
            return frontendProperties.redirectToHome("installation=pending");
        }
        if (installationId == null || !("install".equals(setupAction) || "update".equals(setupAction))) {
            return frontendProperties.redirectToHome("installation=failed");
        }
        try {
            return canAccessInstallation(installationId, authentication)
                    ? frontendProperties.redirectToHome("installation=success") : frontendProperties.redirectToHome("installation=unverified");
        } catch (GitHubIntegrationException exception) {
            return frontendProperties.redirectToHome("installation=unverified");
        }
    }

    private boolean canAccessInstallation(long installationId, Authentication authentication) {
        return getInstallations(authentication).stream().anyMatch(installation -> installation.id() == installationId);
    }

    private boolean isActiveInstallationForCurrentApp(Map<String, Object> installation) {
        return gitHubProperties.appId().equals(String.valueOf(installation.get("app_id")))
                && installation.get("suspended_at") == null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getObject(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getRows(Map<String, Object> response, String key) {
        return (List<Map<String, Object>>) response.getOrDefault(key, List.of());
    }
}
