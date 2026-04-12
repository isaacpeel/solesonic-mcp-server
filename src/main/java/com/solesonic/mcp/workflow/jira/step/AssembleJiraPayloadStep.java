package com.solesonic.mcp.workflow.jira.step;

import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.jira.CreateJiraWorkflowContext;
import com.solesonic.mcp.workflow.jira.WorkflowStage;
import com.solesonic.mcp.workflow.model.JiraIssueCreatePayload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AssembleJiraPayloadStep implements WorkflowStep<CreateJiraWorkflowContext> {
    public static final String STEP_NAME = "finalize";

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public Mono<WorkflowDecision> execute(CreateJiraWorkflowContext context, WorkflowExecutionContext executionContext) {
        return Mono.fromSupplier(() -> {
            context.setCurrentStage(WorkflowStage.ASSEMBLING_PAYLOAD);
            executionContext.progressTracker().step(name()).update(0.5, "Compiling workflow results");

            JiraIssueCreatePayload payload = new JiraIssueCreatePayload(
                    context.getStorySummary(),
                    context.getDetailedDescription(),
                    context.getAcceptanceCriteria(),
                    context.getAssigneeLookupResult()
            );

            context.setFinalPayload(payload);
            context.setPayloadValidated(true);
            executionContext.progressTracker().step(name()).done("Create Jira workflow completed");
            return WorkflowDecision.continueWorkflow();
        });
    }
}
