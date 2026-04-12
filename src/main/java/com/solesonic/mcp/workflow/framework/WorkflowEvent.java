package com.solesonic.mcp.workflow.framework;

public record WorkflowEvent(
        WorkflowEventType type,
        String workflowName,
        String stepName,
        String correlationId,
        Integer percent,
        String message,
        Throwable error
) {
    public static WorkflowEvent informational(
            String workflowName,
            String stepName,
            String correlationId,
            Integer percent,
            String message
    ) {
        return new WorkflowEvent(WorkflowEventType.INFORMATIONAL, workflowName, stepName, correlationId, percent, message, null);
    }

    public static WorkflowEvent workflowStarted(String workflowName, String correlationId, String message) {
        return new WorkflowEvent(WorkflowEventType.WORKFLOW_STARTED, workflowName, null, correlationId, null, message, null);
    }

    public static WorkflowEvent stepStarted(String workflowName, String stepName, String correlationId, String message) {
        return new WorkflowEvent(WorkflowEventType.STEP_STARTED, workflowName, stepName, correlationId, null, message, null);
    }

    public static WorkflowEvent stepProgress(
            String workflowName,
            String stepName,
            String correlationId,
            Integer percent,
            String message
    ) {
        return new WorkflowEvent(WorkflowEventType.STEP_PROGRESS, workflowName, stepName, correlationId, percent, message, null);
    }

    public static WorkflowEvent stepCompleted(String workflowName, String stepName, String correlationId, String message) {
        return new WorkflowEvent(WorkflowEventType.STEP_COMPLETED, workflowName, stepName, correlationId, null, message, null);
    }

    public static WorkflowEvent stepFailed(
            String workflowName,
            String stepName,
            String correlationId,
            String message,
            Throwable error
    ) {
        return new WorkflowEvent(WorkflowEventType.STEP_FAILED, workflowName, stepName, correlationId, null, message, error);
    }

    public static WorkflowEvent workflowCompleted(String workflowName, String correlationId, String message) {
        return new WorkflowEvent(WorkflowEventType.WORKFLOW_COMPLETED, workflowName, null, correlationId, null, message, null);
    }

    public static WorkflowEvent workflowFailed(
            String workflowName,
            String correlationId,
            String message,
            Throwable error
    ) {
        return new WorkflowEvent(WorkflowEventType.WORKFLOW_FAILED, workflowName, null, correlationId, null, message, error);
    }

    public static WorkflowEvent userInputRequired(
            String workflowName,
            String stepName,
            String correlationId,
            String message
    ) {
        return new WorkflowEvent(WorkflowEventType.USER_INPUT_REQUIRED, workflowName, stepName, correlationId, null, message, null);
    }
}
