package com.solesonic.mcp.jira.token;

import java.time.Instant;
import java.util.List;

/**
 * Represents an OAuth2 token set for Atlassian Jira.
 * Tokens should be encrypted at rest. This is Phase 0 scaffolding only.
 */
public record StoredToken(
        String tokenType,
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        List<String> scopes
) {
}
