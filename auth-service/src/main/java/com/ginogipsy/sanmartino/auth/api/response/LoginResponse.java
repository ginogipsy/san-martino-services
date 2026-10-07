package com.ginogipsy.sanmartino.auth.api.response;

// DTO di output (mappato sulla risposta di Keycloak)
import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
        @JsonProperty("access_token") String access_token,
        @JsonProperty("refresh_token") String refresh_token,
        @JsonProperty("expires_in") Long expires_in,
        @JsonProperty("token_type") String token_type
) {}