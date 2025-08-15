package com.solesonic.mcp.jira.client;

import java.util.Collections;
import java.util.List;

/**
 * No-op Jira client used for Phase 0 scaffolding.
 * This does not perform any network calls and simply returns placeholders.
 */
public class NoopJiraClient implements JiraClient {
    @Override
    public CreateIssueResult createIssue(Object requestBody) {
        return new CreateIssueResult("noop", "NOOP-0", "");
    }

    @Override
    public List<AssignableUser> searchAssignableUsers(String query, String projectId) {
        return Collections.emptyList();
    }

    @Override
    public Myself getMyself() {
        return null;
    }
}
