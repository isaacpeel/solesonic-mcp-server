package com.solesonic.mcp.workflow.jira.step;

import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.jira.CreateJiraWorkflowContext;
import com.solesonic.mcp.workflow.jira.WorkflowStage;
import com.solesonic.mcp.workflow.jira.skill.AssigneeResolutionSkill;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ResolveAssigneeStep implements WorkflowStep<CreateJiraWorkflowContext> {
    public static final String STEP_NAME = "assignee-lookup";

    private final AssigneeResolutionSkill assigneeResolutionSkill;

    public ResolveAssigneeStep(AssigneeResolutionSkill assigneeResolutionSkill) {
        this.assigneeResolutionSkill = assigneeResolutionSkill;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public boolean isParallelSafe() {
        return true;
    }

    @Override
    public Mono<WorkflowDecision> execute(CreateJiraWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(WorkflowStage.RESOLVING_ASSIGNEE);

        return assigneeResolutionSkill.resolve(
                        context.getOriginalUserMessage(),
                        executionContext.progressTracker().step(name())
                )
                .map(assigneeLookupResult -> {
                    context.setAssigneeLookupResult(assigneeLookupResult);
                    return WorkflowDecision.continueWorkflow();
                });
    }
}
