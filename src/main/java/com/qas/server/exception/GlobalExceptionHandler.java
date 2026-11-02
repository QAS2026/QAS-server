package com.qas.server.exception;

import com.qas.server.dto.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(GitHubIntegrationException.class)
    public ResponseEntity<ApiErrorResponse> handleGitHubIntegrationException(GitHubIntegrationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(exception.getMessage()));
    }
}
