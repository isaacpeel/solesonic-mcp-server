package com.solesonic.mcp.workflow.sports;

import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import com.solesonic.mcp.workflow.framework.WorkflowRunner;
import org.springframework.stereotype.Service;

@Service
public class SportsResearchWorkflowService {
    private final WorkflowRunner workflowRunner;
    private final SportsResearchWorkflowDefinition sportsResearchWorkflowDefinition;

    public SportsResearchWorkflowService(
            WorkflowRunner workflowRunner,
            SportsResearchWorkflowDefinition sportsResearchWorkflowDefinition
    ) {
        this.workflowRunner = workflowRunner;
        this.sportsResearchWorkflowDefinition = sportsResearchWorkflowDefinition;
    }

    public WorkflowOutcome run(
            SportsResearchWorkflowContext workflowContext,
            WorkflowExecutionContext executionContext
    ) {
        executionContext.progressTracker().startup("Starting sports research workflow");

        WorkflowOutcome outcome = workflowRunner.run(
                sportsResearchWorkflowDefinition.definition(), workflowContext, executionContext);

        workflowContext.setWorkflowStatus(outcome);

        if (outcome == WorkflowOutcome.FAILED) {
            workflowContext.setCurrentStage(SportsWorkflowStage.FAILED);
        }

        if (outcome == WorkflowOutcome.COMPLETED) {
            workflowContext.setCurrentStage(SportsWorkflowStage.COMPLETED);
        }

        return outcome;
    }
}
