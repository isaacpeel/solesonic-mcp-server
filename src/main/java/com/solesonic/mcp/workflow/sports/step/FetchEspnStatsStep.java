package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.model.tavily.TavilyExtractResponse;
import com.solesonic.mcp.service.tavily.TavilySearchService;
import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.EspnTeamProfile;
import com.solesonic.mcp.workflow.sports.model.SportsQuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class FetchEspnStatsStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "fetch-espn-stats";

    private static final Logger log = LoggerFactory.getLogger(FetchEspnStatsStep.class);

    private static final Set<SportsQuestionType> STATS_RELEVANT_TYPES = Set.of(
            SportsQuestionType.GAME_PREVIEW,
            SportsQuestionType.PLAYER_ANALYSIS
    );

    private final TavilySearchService tavilySearchService;

    public FetchEspnStatsStep(TavilySearchService tavilySearchService) {
        this.tavilySearchService = tavilySearchService;
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
    public WorkflowDecision execute(SportsResearchWorkflowContext context, WorkflowExecutionContext executionContext) {
        List<EspnTeamProfile> resolvedTeams = context.getResolvedTeams();
        if (resolvedTeams == null || resolvedTeams.isEmpty()) {
            log.info("No resolved teams — skipping ESPN stats fetch");
            return WorkflowDecision.skip("No teams resolved for stats fetch");
        }

        SportsQuestionType questionType = context.getSportsQueryIntent() != null
                ? context.getSportsQueryIntent().resolvedQuestionType()
                : SportsQuestionType.GENERAL_NEWS;

        if (!STATS_RELEVANT_TYPES.contains(questionType)) {
            log.info("Skipping stats fetch for question type: {}", questionType);
            return WorkflowDecision.skip("Statistics not needed for question type: " + questionType);
        }

        context.setCurrentStage(SportsWorkflowStage.FETCHING_ESPN_STATS);
        executionContext.progressTracker().step(name()).update(0.2, "Fetching team statistics from ESPN");

        List<String> statsUrls = resolvedTeams.stream()
                .map(EspnTeamProfile::statsUrl)
                .toList();

        log.info("Fetching ESPN stats pages: {}", statsUrls);

        try {
            TavilyExtractResponse response = tavilySearchService.extract(statsUrls);
            String statsData = formatExtractResults(response);
            context.setEspnStatsData(statsData);
            executionContext.progressTracker().step(name()).done("ESPN stats data fetched");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to fetch ESPN stats pages", exception);
            context.setEspnStatsData("ESPN statistics data unavailable.");
            return WorkflowDecision.continueWorkflow();
        }
    }

    private String formatExtractResults(TavilyExtractResponse response) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return "No ESPN statistics data retrieved.";
        }
        StringBuilder builder = new StringBuilder();
        for (var result : response.results()) {
            builder.append("=== ESPN Stats: ").append(result.url()).append(" ===\n");
            builder.append(result.rawContent()).append("\n\n");
        }
        return builder.toString();
    }
}
