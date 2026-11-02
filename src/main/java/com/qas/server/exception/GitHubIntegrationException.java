package com.qas.server.exception;

public class GitHubIntegrationException extends RuntimeException {
    private final int status;

    public GitHubIntegrationException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
