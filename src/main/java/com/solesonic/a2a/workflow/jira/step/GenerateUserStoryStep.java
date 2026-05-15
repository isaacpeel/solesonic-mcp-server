package com.solesonic.a2a.workflow.jira.step;

import com.solesonic.a2a.workflow.framework.WorkflowDecision;
import com.solesonic.a2a.workflow.framework.WorkflowExecutionContext;
import com.solesonic.a2a.workflow.framework.WorkflowStep;
import com.solesonic.a2a.workflow.jira.CreateJiraWorkflowContext;
import com.solesonic.a2a.workflow.jira.WorkflowStage;
import com.solesonic.a2a.workflow.jira.skill.UserStoryGenerationSkill;
import org.springframework.stereotype.Component;

@Component
public class GenerateUserStoryStep implements WorkflowStep<CreateJiraWorkflowContext> {
    public static final String STEP_NAME = "user-story-generation";

    private final UserStoryGenerationSkill userStoryGenerationSkill;

    public GenerateUserStoryStep(UserStoryGenerationSkill userStoryGenerationSkill) {
        this.userStoryGenerationSkill = userStoryGenerationSkill;
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
        context.setCurrentStage(WorkflowStage.GENERATING_USER_STORY);

        var userStoryResult = userStoryGenerationSkill.generate(
                context.userMessage(),
                executionContext.progressTracker().step(name())
        );

        context.setStorySummary(userStoryResult.getSummary());
        context.setDetailedDescription(userStoryResult.getDetailedStory());
        context.setAcceptanceCriteria(userStoryResult.getAcceptanceCriteria());
        return WorkflowDecision.continueWorkflow();
    }
}
