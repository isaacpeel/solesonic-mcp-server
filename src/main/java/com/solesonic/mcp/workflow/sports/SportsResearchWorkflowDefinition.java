package com.solesonic.mcp.workflow.sports;

import com.solesonic.mcp.workflow.framework.WorkflowDefinition;
import com.solesonic.mcp.workflow.sports.step.DeepPlayerAnalysisStep;
import com.solesonic.mcp.workflow.sports.step.FetchEspnRosterStep;
import com.solesonic.mcp.workflow.sports.step.FetchEspnScheduleStep;
import com.solesonic.mcp.workflow.sports.step.FetchEspnStandingsStep;
import com.solesonic.mcp.workflow.sports.step.FetchEspnStatsStep;
import com.solesonic.mcp.workflow.sports.step.ParseSportsIntentStep;
import com.solesonic.mcp.workflow.sports.step.ResolveEspnTeamUrlsStep;
import com.solesonic.mcp.workflow.sports.step.SearchSportsNewsStep;
import com.solesonic.mcp.workflow.sports.step.SynthesizeSportsAnalysisStep;
import com.solesonic.mcp.workflow.sports.step.ValidateRosterAndDatesStep;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SportsResearchWorkflowDefinition {
    public static final String WORKFLOW_NAME = "sports-research-workflow";

    private final WorkflowDefinition<SportsResearchWorkflowContext> workflowDefinition;

    public SportsResearchWorkflowDefinition(
            ParseSportsIntentStep parseSportsIntentStep,
            ResolveEspnTeamUrlsStep resolveEspnTeamUrlsStep,
            FetchEspnScheduleStep fetchEspnScheduleStep,
            FetchEspnRosterStep fetchEspnRosterStep,
            FetchEspnStatsStep fetchEspnStatsStep,
            FetchEspnStandingsStep fetchEspnStandingsStep,
            SearchSportsNewsStep searchSportsNewsStep,
            ValidateRosterAndDatesStep validateRosterAndDatesStep,
            DeepPlayerAnalysisStep deepPlayerAnalysisStep,
            SynthesizeSportsAnalysisStep synthesizeSportsAnalysisStep
    ) {
        workflowDefinition = WorkflowDefinition.<SportsResearchWorkflowContext>builder(WORKFLOW_NAME)
                // [GPU0] Parse user intent from natural language
                .sequential(parseSportsIntentStep)
                // [No LLM] Map team names to authoritative ESPN URL profiles
                .sequential(resolveEspnTeamUrlsStep)
                // [No GPU] Parallel: fetch authoritative ESPN data + news search
                .parallel(
                        fetchEspnScheduleStep,    // extract ESPN schedule page
                        fetchEspnRosterStep,      // extract ESPN roster page
                        fetchEspnStatsStep,       // extract ESPN team stats page
                        fetchEspnStandingsStep,   // extract ESPN standings page
                        searchSportsNewsStep      // Tavily search for news/injuries/trades
                )
                // [GPU1] Validate player roster status using ESPN data as authoritative source
                .sequential(validateRosterAndDatesStep)
                // [GPU1] Optional deep-dive — only runs for PLAYER_ANALYSIS with a named player
                .sequential(deepPlayerAnalysisStep)
                // [GPU0] Synthesize all data into the final analyst response
                .sequential(synthesizeSportsAnalysisStep)
                .build();
    }

    public WorkflowDefinition<SportsResearchWorkflowContext> definition() {
        return workflowDefinition;
    }

    public Map<String, Double> stepWeights() {
        return Map.of(
                ParseSportsIntentStep.STEP_NAME,        0.08,
                ResolveEspnTeamUrlsStep.STEP_NAME,      0.02,
                FetchEspnScheduleStep.STEP_NAME,        0.12,
                FetchEspnRosterStep.STEP_NAME,          0.10,
                FetchEspnStatsStep.STEP_NAME,           0.10,
                FetchEspnStandingsStep.STEP_NAME,       0.06,
                SearchSportsNewsStep.STEP_NAME,         0.10,
                ValidateRosterAndDatesStep.STEP_NAME,   0.17,
                DeepPlayerAnalysisStep.STEP_NAME,       0.10,
                SynthesizeSportsAnalysisStep.STEP_NAME, 0.15
        );
    }
}
