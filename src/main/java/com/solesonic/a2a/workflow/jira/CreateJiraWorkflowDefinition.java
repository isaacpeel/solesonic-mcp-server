package com.solesonic.a2a.workflow.jira;

import com.solesonic.a2a.workflow.framework.WorkflowDefinition;
import com.solesonic.a2a.workflow.jira.step.AssembleJiraPayloadStep;
import com.solesonic.a2a.workflow.jira.step.GenerateUserStoryStep;
import com.solesonic.a2a.workflow.jira.step.ResolveAssigneeStep;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CreateJiraWorkflowDefinition {
    public static final String WORKFLOW_NAME = "create-jira-workflow";

    private final WorkflowDefinition<CreateJiraWorkflowContext> workflowDefinition;

    public CreateJiraWorkflowDefinition(
            GenerateUserStoryStep generateUserStoryStep,
            ResolveAssigneeStep resolveAssigneeStep,
            AssembleJiraPayloadStep assembleJiraPayloadStep
    ) {
        workflowDefinition = WorkflowDefinition.<CreateJiraWorkflowContext>builder(WORKFLOW_NAME)
                .parallel(generateUserStoryStep, resolveAssigneeStep)
                .sequential(assembleJiraPayloadStep)
                .build();
    }

    public WorkflowDefinition<CreateJiraWorkflowContext> definition() {
        return workflowDefinition;
    }

    public Map<String, Double> stepWeights() {
        return Map.of(
                GenerateUserStoryStep.STEP_NAME, 0.70,
                ResolveAssigneeStep.STEP_NAME, 0.20,
                AssembleJiraPayloadStep.STEP_NAME, 0.10
        );
    }
}
