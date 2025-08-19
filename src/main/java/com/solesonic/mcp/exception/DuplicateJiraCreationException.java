package com.solesonic.mcp.exception;


import com.solesonic.mcp.model.atlassian.jira.JiraIssue;

public class DuplicateJiraCreationException extends RuntimeException {
    private JiraIssue jiraIssue;

    public DuplicateJiraCreationException(String message) {
        super(message);
    }

    public DuplicateJiraCreationException(JiraIssue jiraIssue) {
        this.jiraIssue = jiraIssue;
    }

    public JiraIssue getJiraIssue() {
        return jiraIssue;
    }
}
