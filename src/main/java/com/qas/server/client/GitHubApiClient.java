package com.qas.server.client;

import java.util.Map;
import com.qas.server.exception.GitHubIntegrationException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GitHubApiClient {
    private final RestClient restClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public GitHubApiClient(RestClient githubRestClient, OAuth2AuthorizedClientManager authorizedClientManager) {
        this.restClient = githubRestClient;
        this.authorizedClientManager = authorizedClientManager;
    }

    public Map<String, Object> fetchInstallationsPage(int page, Authentication authentication) {
        return get("/user/installations?per_page=100&page=" + page, authentication);
    }

    public Map<String, Object> fetchRepositoriesPage(long installationId, int page, Authentication authentication) {
        return get("/user/installations/" + installationId + "/repositories?per_page=30&page=" + page, authentication);
    }

    private Map<String, Object> get(String path, Authentication authentication) {
        try {
            var authorizedClient = authorizedClientManager.authorize(
                    OAuth2AuthorizeRequest.withClientRegistrationId("github").principal(authentication).build());
            if (authorizedClient == null) {
                throw new GitHubIntegrationException(401, "GitHub에 다시 로그인해 주세요.");
            }

            var response = restClient.get().uri(path)
                    .headers(headers -> headers.setBearerAuth(authorizedClient.getAccessToken().getTokenValue()))
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null) {
                throw new GitHubIntegrationException(502, "GitHub가 빈 응답을 반환했습니다.");
            }
            return response;
        } catch (OAuth2AuthorizationException exception) {
            throw new GitHubIntegrationException(401, "GitHub 인증이 만료되었습니다. 다시 로그인해 주세요.");
        } catch (RestClientResponseException exception) {
            throw translateHttpException(exception);
        } catch (RestClientException exception) {
            throw new GitHubIntegrationException(502, "GitHub에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private GitHubIntegrationException translateHttpException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401) {
            return new GitHubIntegrationException(401, "GitHub에 다시 로그인해 주세요.");
        }
        int responseStatus = status == 403 || status == 429 ? 503 : 502;
        return new GitHubIntegrationException(responseStatus,
                "GitHub 조회가 제한되었거나 권한이 변경되었습니다. 잠시 후 다시 시도해 주세요.");
    }
}
