package com.qas.server.dto;

public record CsrfTokenResponse(String token, String headerName) {}
