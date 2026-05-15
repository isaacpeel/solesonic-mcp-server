package com.solesonic.a2a.workflow;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.a2a.workflow.agile.AgileQueryWorkflowContext;
import com.solesonic.a2a.workflow.agile.AgileQueryWorkflowDefinition;
import com.solesonic.a2a.workflow.agile.AgileQueryWorkflowService;
import com.solesonic.a2a.workflow.framework.WorkflowExecutionContext;
import com.solesonic.a2a.workflow.framework.WorkflowExecutionContextFactory;
import com.solesonic.a2a.workflow.framework.WorkflowOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

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

    public AgileQueryWorkflowContext startWorkflow(McpSyncRequestContext mcpSyncRequestContext, String userMessage) {
        log.info("Starting Agile workflow");
        WorkflowExecutionContext executionContext = executionContextFactory.create(
                mcpSyncRequestContext,
                AgileQueryWorkflowDefinition.WORKFLOW_NAME,
                agileQueryWorkflowDefinition.stepWeights()
        );

        AgileQueryWorkflowContext workflowContext = new AgileQueryWorkflowContext(userMessage);
        workflowContext.setExecutionContext(executionContext);

        WorkflowOutcome outcome = agileQueryWorkflowService.run(workflowContext, executionContext);

        if (outcome == WorkflowOutcome.USER_INPUT_REQUIRED) {
            throw new JiraException("Agile query workflow paused while waiting for user input");
        }

        if (outcome == WorkflowOutcome.FAILED) {
            throw new JiraException("Agile query workflow failed");
        }

        return workflowContext;
    }

    public AgileQueryWorkflowContext startWorkflow(
            String userMessage,
            BiConsumer<Integer, String> progressEmitter
    ) {
        log.info("Starting Agile workflow");
        WorkflowExecutionContext executionContext = executionContextFactory.create(
                progressEmitter,
                AgileQueryWorkflowDefinition.WORKFLOW_NAME,
                agileQueryWorkflowDefinition.stepWeights()
        );

        AgileQueryWorkflowContext workflowContext = new AgileQueryWorkflowContext(userMessage);
        workflowContext.setExecutionContext(executionContext);

        WorkflowOutcome outcome = agileQueryWorkflowService.run(workflowContext, executionContext);

        if (outcome == WorkflowOutcome.FAILED) {
            throw new IllegalStateException("Agile query workflow failed");
        }

        return workflowContext;
    }
}
