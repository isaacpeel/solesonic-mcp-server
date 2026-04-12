package com.solesonic.mcp.workflow;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.workflow.framework.LoggingWorkflowNotificationService;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowNotificationService;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import com.solesonic.mcp.workflow.framework.WorkflowProgressTracker;
import com.solesonic.mcp.workflow.jira.CreateJiraWorkflowContext;
import com.solesonic.mcp.workflow.jira.CreateJiraWorkflowDefinition;
import com.solesonic.mcp.workflow.jira.CreateJiraWorkflowService;
import com.solesonic.mcp.workflow.jira.step.AssembleJiraPayloadStep;
import com.solesonic.mcp.workflow.jira.step.GenerateUserStoryStep;
import com.solesonic.mcp.workflow.jira.step.ResolveAssigneeStep;
import com.solesonic.mcp.workflow.model.JiraIssueCreatePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
public class CreateJiraWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CreateJiraWorkflow.class);

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CHAT_ID_KEY = "chatId";

    private final CreateJiraWorkflowService createJiraWorkflowService;

    public CreateJiraWorkflow(CreateJiraWorkflowService createJiraWorkflowService) {
        this.createJiraWorkflowService = createJiraWorkflowService;
    }

    public Mono<JiraIssueCreatePayload> startWorkflow(McpAsyncRequestContext mcpAsyncRequestContext, String userMessage) {
        try {
            ProgressReporter progressReporter = new ProgressReporter(mcpAsyncRequestContext);
            WorkflowNotificationService notificationService = new LoggingWorkflowNotificationService(progressReporter);
            Map<String, Object> requestMetadata = mcpAsyncRequestContext.requestMeta();
            String correlationId = resolveCorrelationId(requestMetadata);

            WorkflowProgressTracker workflowProgressTracker = new WorkflowProgressTracker(
                    CreateJiraWorkflowDefinition.WORKFLOW_NAME,
                    correlationId,
                    notificationService,
                    Map.of(
                            GenerateUserStoryStep.STEP_NAME, 0.70,
                            ResolveAssigneeStep.STEP_NAME, 0.20,
                            AssembleJiraPayloadStep.STEP_NAME, 0.10
                    )
            );

            WorkflowExecutionContext executionContext = new WorkflowExecutionContext(
                    CreateJiraWorkflowDefinition.WORKFLOW_NAME,
                    correlationId,
                    notificationService,
                    workflowProgressTracker,
                    requestMetadata
            );

            CreateJiraWorkflowContext workflowContext = new CreateJiraWorkflowContext(userMessage);

            return createJiraWorkflowService.run(workflowContext, executionContext)
                    .flatMap(outcome -> mapOutcomeToPayload(outcome, workflowContext));
        } catch (Exception exception) {
            log.error("Create Jira workflow failed", exception);
            return Mono.error(new JiraException("Create Jira workflow failed: " + exception.getMessage(), exception));
        }
    }

    private Mono<JiraIssueCreatePayload> mapOutcomeToPayload(
            WorkflowOutcome workflowOutcome,
            CreateJiraWorkflowContext workflowContext
    ) {
        if (workflowOutcome == WorkflowOutcome.USER_INPUT_REQUIRED) {
            return Mono.error(new JiraException("Create Jira workflow paused while waiting for user input"));
        }

        if (workflowOutcome == WorkflowOutcome.FAILED) {
            return Mono.error(new JiraException("Create Jira workflow failed"));
        }

        JiraIssueCreatePayload payload = workflowContext.getFinalPayload();

        if (payload == null) {
            return Mono.error(new JiraException("Create Jira workflow completed without payload"));
        }

        return Mono.just(payload);
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