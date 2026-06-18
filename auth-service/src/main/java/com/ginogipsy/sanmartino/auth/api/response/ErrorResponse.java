package com.ginogipsy.sanmartino.auth.api.response;

public record ErrorResponse(
        int status,
        String message,
        long timestamp
) {}