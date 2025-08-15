package com.solesonic.mcp.jira.model;

/**
 * Output contract for create_jira_issue.
 */
public record CreateJiraIssueResponse(
        String issueId,
        String issueKey,
        String issueUri,
        String duplicateOf
) {}
