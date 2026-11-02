package com.qas.server.dto;

public record GitHubRepositoryResponse(long id, String fullName, boolean privateRepository, String htmlUrl) {}
