package com.solesonic.mcp.workflow.agile;

import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import com.solesonic.mcp.workflow.framework.WorkflowRunner;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AgileQueryWorkflowService {
    private final WorkflowRunner workflowRunner;
    private final AgileQueryWorkflowDefinition agileQueryWorkflowDefinition;

    public AgileQueryWorkflowService(
            WorkflowRunner workflowRunner,
            AgileQueryWorkflowDefinition agileQueryWorkflowDefinition
    ) {
        this.workflowRunner = workflowRunner;
        this.agileQueryWorkflowDefinition = agileQueryWorkflowDefinition;
    }

    public Mono<WorkflowOutcome> run(
            AgileQueryWorkflowContext workflowContext,
            WorkflowExecutionContext executionContext
    ) {
        executionContext.progressTracker().startup("Parsing your request");
        executionContext.progressTracker().startup("Fetching boards");
        executionContext.progressTracker().startup("Workflow started");

        return workflowRunner.run(agileQueryWorkflowDefinition.definition(), workflowContext, executionContext)
                .doOnNext(outcome -> {
                    workflowContext.setWorkflowStatus(outcome);

                    if (outcome == WorkflowOutcome.FAILED) {
                        workflowContext.setCurrentStage(AgileWorkflowStage.FAILED);
                    }

                    if (outcome == WorkflowOutcome.COMPLETED) {
                        workflowContext.setCurrentStage(AgileWorkflowStage.COMPLETED);
                    }
                });
    }
}
