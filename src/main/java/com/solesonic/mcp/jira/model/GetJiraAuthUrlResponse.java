package com.solesonic.mcp.jira.model;

import java.time.Instant;

/**
 * Output contract for get_jira_auth_url.
 */
public record GetJiraAuthUrlResponse(
        String url,
        String state,
        Instant expiresAt
) {}
