package com.solesonic.a2a.workflow.framework;

import java.util.Objects;

import static com.solesonic.mcp.prompt.PromptConstants.todayDate;

public abstract class WorkflowContext<S extends Enum<S>> {
    private final String userMessage;
    private final String currentDateTime;
    private WorkflowOutcome workflowStatus;
    private WorkflowExecutionContext executionContext;

    protected WorkflowContext(String userMessage) {
        this.userMessage = Objects.requireNonNull(userMessage);
        this.currentDateTime = todayDate();
    }

    public String userMessage() { return userMessage; }
    public String currentDateTime() { return currentDateTime; }

    public abstract S currentStage();
    public abstract void setCurrentStage(S stage);

    public WorkflowOutcome workflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(WorkflowOutcome workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public WorkflowExecutionContext executionContext() {
        return executionContext;
    }

    public void setExecutionContext(WorkflowExecutionContext executionContext) {
        this.executionContext = executionContext;
    }
}
