package com.solesonic.mcp.jira.model;

/**
 * Output contract for test_jira_auth.
 */
public record TestJiraAuthResponse(
        boolean ok,
        String accountId,
        String email
) {}
