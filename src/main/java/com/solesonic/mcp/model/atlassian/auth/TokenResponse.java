package com.solesonic.mcp.model.atlassian.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;
import java.util.UUID;

public record TokenResponse(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("expiresInSeconds") int expiresInSeconds,
        @JsonProperty("issuedAt") ZonedDateTime issuedAt,
        @JsonProperty("userId") UUID userId,
        @JsonProperty("siteId") String siteId) { }