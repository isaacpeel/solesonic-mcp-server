package com.solesonic.mcp.workflow;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowContext;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowDefinition;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowService;
import com.solesonic.mcp.workflow.agile.step.ListBoardsStep;
import com.solesonic.mcp.workflow.agile.step.ParseAgileIntentStep;
import com.solesonic.mcp.workflow.framework.LoggingWorkflowNotificationService;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowNotificationService;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import com.solesonic.mcp.workflow.framework.WorkflowProgressTracker;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
public class AgileQueryWorkflow {
    private static final Logger log = LoggerFactory.getLogger(AgileQueryWorkflow.class);

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CHAT_ID_KEY = "chatId";

    private final AgileQueryWorkflowService agileQueryWorkflowService;

    public AgileQueryWorkflow(AgileQueryWorkflowService agileQueryWorkflowService) {
        this.agileQueryWorkflowService = agileQueryWorkflowService;
    }

    public Mono<AgileQueryWorkflowContext> startWorkflow(McpAsyncRequestContext mcpAsyncRequestContext, String userMessage) {
        try {
            ProgressReporter progressReporter = new ProgressReporter(mcpAsyncRequestContext);
            WorkflowNotificationService notificationService = new LoggingWorkflowNotificationService(progressReporter);
            Map<String, Object> requestMetadata = mcpAsyncRequestContext.requestMeta();
            String correlationId = resolveCorrelationId(requestMetadata);

            WorkflowExecutionContext executionContext = getExecutionContext(correlationId, notificationService, requestMetadata);

            AgileQueryWorkflowContext workflowContext = new AgileQueryWorkflowContext(userMessage);

            return agileQueryWorkflowService.run(workflowContext, executionContext)
                    .flatMap(outcome -> mapOutcomeToContext(outcome, workflowContext));
        } catch (Exception exception) {
            log.error("Agile query workflow failed", exception);
            return Mono.error(new JiraException("Agile query workflow failed: " + exception.getMessage(), exception));
        }
    }

    private static @NonNull WorkflowExecutionContext getExecutionContext(String correlationId, WorkflowNotificationService notificationService, Map<String, Object> requestMetadata) {
        WorkflowProgressTracker workflowProgressTracker = new WorkflowProgressTracker(
                AgileQueryWorkflowDefinition.WORKFLOW_NAME,
                correlationId,
                notificationService,
                Map.of(
                        ParseAgileIntentStep.STEP_NAME, 0.50,
                        ListBoardsStep.STEP_NAME, 0.50
                )
        );

        return new WorkflowExecutionContext(
                AgileQueryWorkflowDefinition.WORKFLOW_NAME,
                correlationId,
                notificationService,
                workflowProgressTracker,
                requestMetadata
        );
    }

    private Mono<AgileQueryWorkflowContext> mapOutcomeToContext(
            WorkflowOutcome workflowOutcome,
            AgileQueryWorkflowContext workflowContext
    ) {
        if (workflowOutcome == WorkflowOutcome.FAILED) {
            return Mono.error(new JiraException("Agile query workflow failed"));
        }

        return Mono.just(workflowContext);
    }

    private String resolveCorrelationId(Map<String, Object> requestMetadata) {
        if (requestMetadata == null || requestMetadata.isEmpty()) {
            return UUID.randomUUID().toString();
        }

        if (requestMetadata.containsKey(CORRELATION_ID_KEY)) {
            return requestMetadata.get(CORRELATION_ID_KEY).toString();
        }

        if (requestMetadata.containsKey(CHAT_ID_KEY)) {
            return requestMetadata.get(CHAT_ID_KEY).toString();
        }

        return UUID.randomUUID().toString();
    }
}
