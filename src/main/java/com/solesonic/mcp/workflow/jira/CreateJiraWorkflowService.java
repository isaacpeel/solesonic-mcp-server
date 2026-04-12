package com.solesonic.mcp.workflow.jira;

import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowOutcome;
import com.solesonic.mcp.workflow.framework.WorkflowRunner;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CreateJiraWorkflowService {
    private final WorkflowRunner workflowRunner;
    private final CreateJiraWorkflowDefinition createJiraWorkflowDefinition;

    public CreateJiraWorkflowService(
            WorkflowRunner workflowRunner,
            CreateJiraWorkflowDefinition createJiraWorkflowDefinition
    ) {
        this.workflowRunner = workflowRunner;
        this.createJiraWorkflowDefinition = createJiraWorkflowDefinition;
    }

    public Mono<WorkflowOutcome> run(
            CreateJiraWorkflowContext workflowContext,
            WorkflowExecutionContext executionContext
    ) {
        executionContext.progressTracker().startup("Generating user story");
        executionContext.progressTracker().startup("Preparing assignee lookup");
        executionContext.progressTracker().startup("Workflow started");

        return workflowRunner.run(createJiraWorkflowDefinition.definition(), workflowContext, executionContext)
                .doOnNext(outcome -> {
                    workflowContext.setWorkflowStatus(outcome);

                    if (outcome == WorkflowOutcome.USER_INPUT_REQUIRED) {
                        workflowContext.setRequiresUserInput(true);
                        workflowContext.setCurrentStage(WorkflowStage.USER_INPUT_REQUIRED);
                        workflowContext.setPendingInput(executionContext.pendingInput());
                    }

                    if (outcome == WorkflowOutcome.FAILED) {
                        workflowContext.setCurrentStage(WorkflowStage.FAILED);
                    }

                    if (outcome == WorkflowOutcome.COMPLETED) {
                        workflowContext.setCurrentStage(WorkflowStage.COMPLETED);
                    }
                });
    }
}
