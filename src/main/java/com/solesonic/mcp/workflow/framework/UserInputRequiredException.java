package com.solesonic.mcp.workflow.framework;

public class UserInputRequiredException extends RuntimeException {
    private final WorkflowPendingInput pendingInput;

    public UserInputRequiredException(WorkflowPendingInput pendingInput) {
        super("Workflow requires user input before it can continue");
        this.pendingInput = pendingInput;
    }

    public WorkflowPendingInput getPendingInput() {
        return pendingInput;
    }
}
