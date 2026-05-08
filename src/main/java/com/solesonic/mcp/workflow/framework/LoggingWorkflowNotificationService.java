package com.solesonic.mcp.workflow.framework;

import com.solesonic.mcp.workflow.ProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class LoggingWorkflowNotificationService implements WorkflowNotificationService {
    private static final Logger log = LoggerFactory.getLogger(LoggingWorkflowNotificationService.class);

    private final ProgressReporter progressReporter;

    public LoggingWorkflowNotificationService(ProgressReporter progressReporter) {
        this.progressReporter = Objects.requireNonNull(progressReporter, "progressReporter must not be null");
    }

    @Override
    public void publish(WorkflowEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        if (event.percent() != null) {
            progressReporter.emit(event.percent(), event.message());
        }

        String workflowName = event.workflowName() == null ? "" : event.workflowName();
        String stepName = event.stepName() == null ? "" : event.stepName();
        String correlationId = event.correlationId() == null ? "" : event.correlationId();
        String message = event.message() == null ? "" : event.message();

        if (event.error() != null) {
            log.error(
                    "workflow_event type={} workflow={} step={} correlationId={} message={}",
                    event.type(),
                    workflowName,
                    stepName,
                    correlationId,
                    message,
                    event.error()
            );
            return;
        }

        log.info(
                "workflow_event type={} workflow={} step={} correlationId={} message={}",
                event.type(),
                workflowName,
                stepName,
                correlationId,
                message
        );
    }
}
