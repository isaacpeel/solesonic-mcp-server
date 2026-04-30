package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.model.tavily.TavilyExtractResponse;
import com.solesonic.mcp.service.tavily.TavilySearchService;
import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.SportsQuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class FetchEspnStandingsStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "fetch-espn-standings";

    private static final Logger log = LoggerFactory.getLogger(FetchEspnStandingsStep.class);

    private static final String ESPN_STANDINGS_URL = "https://www.espn.com/nba/standings";

    private static final Set<SportsQuestionType> STANDINGS_RELEVANT_TYPES = Set.of(
            SportsQuestionType.STANDINGS,
            SportsQuestionType.GAME_PREVIEW
    );

    private final TavilySearchService tavilySearchService;

    public FetchEspnStandingsStep(TavilySearchService tavilySearchService) {
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
        SportsQuestionType questionType = context.getSportsQueryIntent() != null
                ? context.getSportsQueryIntent().resolvedQuestionType()
                : SportsQuestionType.GENERAL_NEWS;

        if (!STANDINGS_RELEVANT_TYPES.contains(questionType)) {
            log.info("Skipping standings fetch for question type: {}", questionType);
            return WorkflowDecision.skip("Standings not needed for question type: " + questionType);
        }

        context.setCurrentStage(SportsWorkflowStage.FETCHING_ESPN_STANDINGS);
        executionContext.progressTracker().step(name()).update(0.2, "Fetching NBA standings from ESPN");

        log.info("Fetching ESPN standings: {}", ESPN_STANDINGS_URL);

        try {
            TavilyExtractResponse response = tavilySearchService.extract(List.of(ESPN_STANDINGS_URL));
            String standingsData = formatExtractResults(response);
            context.setEspnStandingsData(standingsData);
            executionContext.progressTracker().step(name()).done("ESPN standings data fetched");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to fetch ESPN standings page", exception);
            context.setEspnStandingsData("ESPN standings data unavailable.");
            return WorkflowDecision.continueWorkflow();
        }
    }

    private String formatExtractResults(TavilyExtractResponse response) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return "No ESPN standings data retrieved.";
        }
        StringBuilder builder = new StringBuilder();
        for (var result : response.results()) {
            builder.append("=== ESPN Standings: ").append(result.url()).append(" ===\n");
            builder.append(result.rawContent()).append("\n\n");
        }
        return builder.toString();
    }
}
