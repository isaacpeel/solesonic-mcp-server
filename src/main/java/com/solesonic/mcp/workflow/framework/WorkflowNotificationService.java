package com.solesonic.mcp.workflow.framework;

public interface WorkflowNotificationService {
    void publish(WorkflowEvent event);
}
