package com.solesonic.mcp.jira.model;

/**
 * Input contract for complete_jira_auth.
 */
public record CompleteJiraAuthRequest(
        String code,
        String state
) {}
