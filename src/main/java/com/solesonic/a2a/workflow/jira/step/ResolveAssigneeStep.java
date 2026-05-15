package com.solesonic.a2a.workflow.jira.step;

import com.solesonic.a2a.workflow.framework.WorkflowDecision;
import com.solesonic.a2a.workflow.framework.WorkflowExecutionContext;
import com.solesonic.a2a.workflow.framework.WorkflowStep;
import com.solesonic.a2a.workflow.jira.CreateJiraWorkflowContext;
import com.solesonic.a2a.workflow.jira.WorkflowStage;
import com.solesonic.a2a.workflow.jira.skill.AssigneeResolutionSkill;
import org.springframework.stereotype.Component;

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
    public WorkflowDecision execute(CreateJiraWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(WorkflowStage.RESOLVING_ASSIGNEE);

        var assigneeLookupResult = assigneeResolutionSkill.resolve(
                context.userMessage(),
                executionContext.progressTracker().step(name())
        );

        context.setAssigneeLookupResult(assigneeLookupResult);
        return WorkflowDecision.continueWorkflow();
    }
}
