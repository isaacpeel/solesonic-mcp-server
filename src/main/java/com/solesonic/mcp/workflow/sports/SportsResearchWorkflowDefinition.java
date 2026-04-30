package com.solesonic.mcp.workflow.sports;

import com.solesonic.mcp.workflow.framework.WorkflowDefinition;
import com.solesonic.mcp.workflow.sports.step.ParseSportsIntentStep;
import com.solesonic.mcp.workflow.sports.step.SearchCurrentScheduleStep;
import com.solesonic.mcp.workflow.sports.step.SearchSportsNewsStep;
import com.solesonic.mcp.workflow.sports.step.SearchStatisticsStep;
import com.solesonic.mcp.workflow.sports.step.SynthesizeSportsAnalysisStep;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SportsResearchWorkflowDefinition {
    public static final String WORKFLOW_NAME = "sports-research-workflow";

    private final WorkflowDefinition<SportsResearchWorkflowContext> workflowDefinition;

    public SportsResearchWorkflowDefinition(
            ParseSportsIntentStep parseSportsIntentStep,
            SearchCurrentScheduleStep searchCurrentScheduleStep,
            SearchSportsNewsStep searchSportsNewsStep,
            SearchStatisticsStep searchStatisticsStep,
            SynthesizeSportsAnalysisStep synthesizeSportsAnalysisStep
    ) {
        workflowDefinition = WorkflowDefinition.<SportsResearchWorkflowContext>builder(WORKFLOW_NAME)
                .sequential(parseSportsIntentStep)
                .parallel(searchCurrentScheduleStep, searchSportsNewsStep, searchStatisticsStep)
                .sequential(synthesizeSportsAnalysisStep)
                .build();
    }

    public WorkflowDefinition<SportsResearchWorkflowContext> definition() {
        return workflowDefinition;
    }

    public Map<String, Double> stepWeights() {
        return Map.of(
                ParseSportsIntentStep.STEP_NAME, 0.10,
                SearchCurrentScheduleStep.STEP_NAME, 0.25,
                SearchSportsNewsStep.STEP_NAME, 0.25,
                SearchStatisticsStep.STEP_NAME, 0.25,
                SynthesizeSportsAnalysisStep.STEP_NAME, 0.15
        );
    }
}
