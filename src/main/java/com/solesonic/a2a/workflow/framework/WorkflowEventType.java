package com.solesonic.a2a.workflow.framework;

public enum WorkflowEventType {
    INFORMATIONAL,
    WORKFLOW_STARTED,
    STEP_STARTED,
    STEP_PROGRESS,
    STEP_COMPLETED,
    STEP_FAILED,
    WORKFLOW_COMPLETED,
    WORKFLOW_FAILED,
    USER_INPUT_REQUIRED
}
