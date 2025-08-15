package com.solesonic.mcp.jira.client;

import java.util.List;

/**
 * Jira client scaffolding (will be implemented with HTTP + OAuth).
 */
public interface JiraClient {

    record CreateIssueResult(String issueId, String issueKey, String issueUri) {}

    record AssignableUser(String accountId, String displayName, String emailAddress) {}

    record Myself(String accountId, String emailAddress) {}

    CreateIssueResult createIssue(Object requestBody);

    List<AssignableUser> searchAssignableUsers(String query, String projectId);

    Myself getMyself();
}
