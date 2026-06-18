package com.ginogipsy.sanmartino.auth.api.response;

// DTO di output (mappato sulla risposta di Keycloak)
public record LoginResponse(
        String access_token,
        String refresh_token,
        Long expires_in,
        String token_type
) {}