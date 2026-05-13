package com.solesonic.mcp.workflow;

import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContextFactory;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowDefinition;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

@Component
public class SportsResearchWorkflow {
    private static final Logger log = LoggerFactory.getLogger(SportsResearchWorkflow.class);

    private final SportsResearchWorkflowService sportsResearchWorkflowService;
    private final SportsResearchWorkflowDefinition sportsResearchWorkflowDefinition;
    private final WorkflowExecutionContextFactory executionContextFactory;

    public SportsResearchWorkflow(
            SportsResearchWorkflowService sportsResearchWorkflowService,
            SportsResearchWorkflowDefinition sportsResearchWorkflowDefinition,
            WorkflowExecutionContextFactory executionContextFactory
    ) {
        this.sportsResearchWorkflowService = sportsResearchWorkflowService;
        this.sportsResearchWorkflowDefinition = sportsResearchWorkflowDefinition;
        this.executionContextFactory = executionContextFactory;
    }

    public SportsResearchWorkflowContext startWorkflow(
            String userMessage,
            BiConsumer<Integer, String> progressEmitter
    ) {
        log.info("Starting NBA research workflow");

        WorkflowExecutionContext executionContext = executionContextFactory.create(
                progressEmitter,
                SportsResearchWorkflowDefinition.WORKFLOW_NAME,
                sportsResearchWorkflowDefinition.stepWeights()
        );

        SportsResearchWorkflowContext workflowContext = new SportsResearchWorkflowContext(userMessage);
        workflowContext.setExecutionContext(executionContext);

        WorkflowOutcome outcome = sportsResearchWorkflowService.run(workflowContext, executionContext);

        if (outcome == WorkflowOutcome.FAILED) {
            throw new IllegalStateException("NBA research workflow failed");
        }

        return workflowContext;
    }

    public SportsResearchWorkflowContext startWorkflow(String userMessage) {
        return startWorkflow(userMessage, (percent, message) -> {});
    }
}
