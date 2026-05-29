package com.solesonic.mcp.workflow.jira;

public enum WorkflowStage {
    INITIALIZING,
    GENERATING_USER_STORY,
    RESOLVING_ASSIGNEE,
    ASSEMBLING_PAYLOAD,
    COMPLETED,
    FAILED,
    USER_INPUT_REQUIRED
}
