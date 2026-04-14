package com.solesonic.mcp.workflow.agile;

import com.solesonic.mcp.workflow.agile.step.ListBoardsStep;
import com.solesonic.mcp.workflow.agile.step.ParseAgileIntentStep;
import com.solesonic.mcp.workflow.framework.WorkflowDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgileQueryWorkflowDefinition {
    public static final String WORKFLOW_NAME = "agile-query-workflow";

    private final WorkflowDefinition<AgileQueryWorkflowContext> workflowDefinition;

    public AgileQueryWorkflowDefinition(
            ParseAgileIntentStep parseAgileIntentStep,
            ListBoardsStep listBoardsStep
    ) {
        workflowDefinition = WorkflowDefinition.<AgileQueryWorkflowContext>builder(WORKFLOW_NAME)
                .parallel(parseAgileIntentStep, listBoardsStep)
                .build();
    }

    public WorkflowDefinition<AgileQueryWorkflowContext> definition() {
        return workflowDefinition;
    }

    public Map<String, Double> stepWeights() {
        return Map.of(
                ParseAgileIntentStep.STEP_NAME, 0.50,
                ListBoardsStep.STEP_NAME, 0.50
        );
    }
}
