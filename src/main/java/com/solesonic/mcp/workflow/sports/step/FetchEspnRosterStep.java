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
public class FetchEspnRosterStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "fetch-espn-roster";

    private static final Logger log = LoggerFactory.getLogger(FetchEspnRosterStep.class);

    private static final Set<SportsQuestionType> ROSTER_RELEVANT_TYPES = Set.of(
            SportsQuestionType.GAME_PREVIEW,
            SportsQuestionType.PLAYER_ANALYSIS,
            SportsQuestionType.TRADE_NEWS
    );

    private final TavilySearchService tavilySearchService;

    public FetchEspnRosterStep(TavilySearchService tavilySearchService) {
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
            log.info("No resolved teams — skipping ESPN roster fetch");
            return WorkflowDecision.skip("No teams resolved for roster fetch");
        }

        SportsQuestionType questionType = context.getSportsQueryIntent() != null
                ? context.getSportsQueryIntent().resolvedQuestionType()
                : SportsQuestionType.GENERAL_NEWS;

        if (!ROSTER_RELEVANT_TYPES.contains(questionType)) {
            log.info("Skipping roster fetch for question type: {}", questionType);
            return WorkflowDecision.skip("Roster not needed for question type: " + questionType);
        }

        context.setCurrentStage(SportsWorkflowStage.FETCHING_ESPN_ROSTER);
        executionContext.progressTracker().step(name()).update(0.2, "Fetching current rosters from ESPN");

        List<String> rosterUrls = resolvedTeams.stream()
                .map(EspnTeamProfile::rosterUrl)
                .toList();

        log.info("Fetching ESPN roster pages: {}", rosterUrls);

        try {
            TavilyExtractResponse response = tavilySearchService.extract(rosterUrls);
            String rosterData = formatExtractResults(response);
            context.setEspnRosterData(rosterData);
            executionContext.progressTracker().step(name()).done("ESPN roster data fetched");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to fetch ESPN roster pages", exception);
            context.setEspnRosterData("ESPN roster data unavailable.");
            return WorkflowDecision.continueWorkflow();
        }
    }

    private String formatExtractResults(TavilyExtractResponse response) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return "No ESPN roster data retrieved.";
        }
        StringBuilder builder = new StringBuilder();
        for (var result : response.results()) {
            builder.append("=== ESPN Roster: ").append(result.url()).append(" ===\n");
            builder.append(result.rawContent()).append("\n\n");
        }
        return builder.toString();
    }
}
