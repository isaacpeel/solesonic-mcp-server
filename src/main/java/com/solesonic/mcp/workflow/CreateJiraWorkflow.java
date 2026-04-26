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
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;

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

    public JiraIssueCreatePayload startWorkflow(McpSyncRequestContext mcpSyncRequestContext, String userMessage) {
        WorkflowExecutionContext executionContext = executionContextFactory.create(
                mcpSyncRequestContext,
                CreateJiraWorkflowDefinition.WORKFLOW_NAME,
                createJiraWorkflowDefinition.stepWeights()
        );

        CreateJiraWorkflowContext workflowContext = new CreateJiraWorkflowContext(userMessage);

        WorkflowOutcome outcome = createJiraWorkflowService.run(workflowContext, executionContext);

        if (outcome == WorkflowOutcome.USER_INPUT_REQUIRED) {
            throw new JiraException("Create Jira workflow paused while waiting for user input");
        }

        if (outcome == WorkflowOutcome.FAILED) {
            throw new JiraException("Create Jira workflow failed");
        }

        JiraIssueCreatePayload payload = workflowContext.getFinalPayload();

        if (payload == null) {
            throw new JiraException("Create Jira workflow completed without payload");
        }

        return payload;
    }
}
