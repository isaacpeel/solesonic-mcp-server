package com.solesonic.mcp.jira.model;

import java.util.List;

/**
 * Input contract for create_jira_issue.
 */
public record CreateJiraIssueRequest(
        String summary,
        String description,
        List<String> acceptanceCriteria,
        String assigneeId,
        Boolean forceCreate
) {
}
