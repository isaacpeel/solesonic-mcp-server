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
        executionContext.progressTracker().startup("Sportsball robot powering up!");

        WorkflowOutcome workflowOutcome = workflowRunner.run(
                sportsResearchWorkflowDefinition.definition(), workflowContext, executionContext);

        if (workflowOutcome == WorkflowOutcome.FAILED) {
            workflowContext.setCurrentStage(SportsWorkflowStage.FAILED);
        }

        if (workflowOutcome == WorkflowOutcome.COMPLETED) {
            workflowContext.setCurrentStage(SportsWorkflowStage.COMPLETED);
        }

        return workflowOutcome;
    }
}
