package com.solesonic.mcp.workflow;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContextFactory;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import com.solesonic.mcp.workflow.jira.CreateJiraWorkflowContext;
import com.solesonic.mcp.workflow.jira.CreateJiraWorkflowDefinition;
import com.solesonic.mcp.workflow.jira.CreateJiraWorkflowService;
import com.solesonic.mcp.workflow.model.JiraIssueCreatePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CreateJiraWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CreateJiraWorkflow.class);

    private final CreateJiraWorkflowService createJiraWorkflowService;
    private final CreateJiraWorkflowDefinition createJiraWorkflowDefinition;
    private final WorkflowExecutionContextFactory executionContextFactory;

    public CreateJiraWorkflow(
            CreateJiraWorkflowService createJiraWorkflowService,
            CreateJiraWorkflowDefinition createJiraWorkflowDefinition,
            WorkflowExecutionContextFactory executionContextFactory
    ) {
        this.createJiraWorkflowService = createJiraWorkflowService;
        this.createJiraWorkflowDefinition = createJiraWorkflowDefinition;
        this.executionContextFactory = executionContextFactory;
    }

    public Mono<JiraIssueCreatePayload> startWorkflow(McpAsyncRequestContext mcpAsyncRequestContext, String userMessage) {
        try {
            WorkflowExecutionContext executionContext = executionContextFactory.create(
                    mcpAsyncRequestContext,
                    CreateJiraWorkflowDefinition.WORKFLOW_NAME,
                    createJiraWorkflowDefinition.stepWeights()
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
}
