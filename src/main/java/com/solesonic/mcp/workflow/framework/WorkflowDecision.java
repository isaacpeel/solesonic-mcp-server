package com.solesonic.mcp.workflow.framework;

import java.util.Objects;

public final class WorkflowDecision {
    private final WorkflowOutcome outcome;
    private final WorkflowPendingInput pendingInput;
    private final String message;

    private WorkflowDecision(WorkflowOutcome outcome, WorkflowPendingInput pendingInput, String message) {
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.pendingInput = pendingInput;
        this.message = message;
    }

    public static WorkflowDecision continueWorkflow() {
        return new WorkflowDecision(WorkflowOutcome.COMPLETED, null, null);
    }

    public static WorkflowDecision failed(String message) {
        return new WorkflowDecision(WorkflowOutcome.FAILED, null, message);
    }

    public static WorkflowDecision userInputRequired(String message, WorkflowPendingInput pendingInput) {
        return new WorkflowDecision(WorkflowOutcome.USER_INPUT_REQUIRED, pendingInput, message);
    }

    /**
     * The step chose not to execute (e.g. a precondition was not met) but the workflow should continue.
     * Distinct from {@link #continueWorkflow()} in intent: use this when the step is intentionally bypassed,
     * so the message is surfaced in observability logs rather than silently omitted.
     */
    @SuppressWarnings("unused")
    public static WorkflowDecision skip(String message) {
        return new WorkflowDecision(WorkflowOutcome.COMPLETED, null, message);
    }

    public WorkflowOutcome outcome() {
        return outcome;
    }

    public WorkflowPendingInput pendingInput() {
        return pendingInput;
    }

    public String message() {
        return message;
    }
}
