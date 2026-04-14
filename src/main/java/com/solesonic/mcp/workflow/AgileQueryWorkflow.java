package com.solesonic.mcp.workflow;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowContext;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowDefinition;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowService;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContextFactory;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AgileQueryWorkflow {
    private static final Logger log = LoggerFactory.getLogger(AgileQueryWorkflow.class);

    private final AgileQueryWorkflowService agileQueryWorkflowService;
    private final AgileQueryWorkflowDefinition agileQueryWorkflowDefinition;
    private final WorkflowExecutionContextFactory executionContextFactory;

    public AgileQueryWorkflow(
            AgileQueryWorkflowService agileQueryWorkflowService,
            AgileQueryWorkflowDefinition agileQueryWorkflowDefinition,
            WorkflowExecutionContextFactory executionContextFactory
    ) {
        this.agileQueryWorkflowService = agileQueryWorkflowService;
        this.agileQueryWorkflowDefinition = agileQueryWorkflowDefinition;
        this.executionContextFactory = executionContextFactory;
    }

    public Mono<AgileQueryWorkflowContext> startWorkflow(McpAsyncRequestContext mcpAsyncRequestContext, String userMessage) {
        try {
            WorkflowExecutionContext executionContext = executionContextFactory.create(
                    mcpAsyncRequestContext,
                    AgileQueryWorkflowDefinition.WORKFLOW_NAME,
                    agileQueryWorkflowDefinition.stepWeights()
            );

            AgileQueryWorkflowContext workflowContext = new AgileQueryWorkflowContext(userMessage);

            return agileQueryWorkflowService.run(workflowContext, executionContext)
                    .flatMap(outcome -> mapOutcomeToContext(outcome, workflowContext));
        } catch (Exception exception) {
            log.error("Agile query workflow failed", exception);
            return Mono.error(new JiraException("Agile query workflow failed: " + exception.getMessage(), exception));
        }
    }

    private Mono<AgileQueryWorkflowContext> mapOutcomeToContext(
            WorkflowOutcome workflowOutcome,
            AgileQueryWorkflowContext workflowContext
    ) {
        if (workflowOutcome == WorkflowOutcome.USER_INPUT_REQUIRED) {
            return Mono.error(new JiraException("Agile query workflow paused while waiting for user input"));
        }

        if (workflowOutcome == WorkflowOutcome.FAILED) {
            return Mono.error(new JiraException("Agile query workflow failed"));
        }

        return Mono.just(workflowContext);
    }
}
