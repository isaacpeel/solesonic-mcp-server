package com.solesonic.mcp.workflow.framework;

import java.util.Map;
import java.util.Objects;

public final class WorkflowExecutionContext {
    private final String workflowName;
    private final String correlationId;
    private final WorkflowNotificationService notificationService;
    private final WorkflowProgressTracker progressTracker;
    private final Map<String, Object> requestMetadata;

    private volatile WorkflowPendingInput pendingInput;

    public WorkflowExecutionContext(
            String workflowName,
            String correlationId,
            WorkflowNotificationService notificationService,
            Map<String, Double> stepWeights,
            Map<String, Object> requestMetadata
    ) {
        this.workflowName = Objects.requireNonNull(workflowName, "workflowName must not be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.progressTracker = new WorkflowProgressTracker(workflowName, correlationId, notificationService, stepWeights);
        this.requestMetadata = requestMetadata == null ? Map.of() : Map.copyOf(requestMetadata);
    }

    public String workflowName() {
        return workflowName;
    }

    public String correlationId() {
        return correlationId;
    }

    public WorkflowNotificationService notificationService() {
        return notificationService;
    }

    public WorkflowProgressTracker progressTracker() {
        return progressTracker;
    }

    @SuppressWarnings("unused")
    public Map<String, Object> requestMetadata() {
        return requestMetadata;
    }

    public WorkflowPendingInput pendingInput() {
        return pendingInput;
    }

    public void setPendingInput(WorkflowPendingInput pendingInput) {
        this.pendingInput = pendingInput;
    }
}
