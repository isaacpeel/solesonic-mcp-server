package com.solesonic.model.atlassian.jira;

public enum IssueStatus {
    IN_PROGRESS("In Progress"),
    TO_DO("To Do"),
    DONE("Done");

    private final String jqlLabel;

    IssueStatus(String jqlLabel) {
        this.jqlLabel = jqlLabel;
    }

    public String toJqlLabel() {
        return jqlLabel;
    }
}
