package com.qas.server.dto;

import java.util.List;

public record GitHubRepositoryPageResponse(int totalCount, int page, List<GitHubRepositoryResponse> repositories) {}
