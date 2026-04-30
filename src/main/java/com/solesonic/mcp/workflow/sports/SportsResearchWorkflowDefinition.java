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
                // [No GPU] Parallel: each step gates itself to the question types that need it
                //   SCHEDULE_LOOKUP  → schedule only
                //   GAME_PREVIEW     → schedule + roster + stats + standings + news
                //   PLAYER_ANALYSIS  → schedule + roster + stats + news
                //   STANDINGS        → standings + news
                //   TRADE_NEWS       → roster + news (with transactions)
                //   GENERAL_NEWS     → news only
                .parallel(
                        fetchEspnScheduleStep,    // SCHEDULE_LOOKUP, GAME_PREVIEW, PLAYER_ANALYSIS
                        fetchEspnRosterStep,      // GAME_PREVIEW, PLAYER_ANALYSIS, TRADE_NEWS
                        fetchEspnStatsStep,       // GAME_PREVIEW, PLAYER_ANALYSIS
                        fetchEspnStandingsStep,   // STANDINGS, GAME_PREVIEW
                        searchSportsNewsStep      // all except SCHEDULE_LOOKUP
                )
                // [GPU1] Parallel: validate and deep-dive run concurrently where both are needed
                //   ValidateRoster → GAME_PREVIEW, PLAYER_ANALYSIS, TRADE_NEWS
                //   DeepPlayer     → PLAYER_ANALYSIS only (Tavily searches first, then GPU1 call)
                .parallel(
                        validateRosterAndDatesStep,
                        deepPlayerAnalysisStep
                )
                // [GPU0] Synthesize all collected data into the final analyst response
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
