package com.solesonic.a2a.workflow.framework;

public interface WorkflowNotificationService {
    void publish(WorkflowEvent event);
}
