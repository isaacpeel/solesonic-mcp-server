package com.solesonic.mcp.jira.model;

import java.util.List;

/**
 * Output contract for complete_jira_auth.
 */
public record CompleteJiraAuthResponse(
        boolean authorized,
        List<String> scopes
) {}
