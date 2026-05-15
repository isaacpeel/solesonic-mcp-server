package com.solesonic.a2a.workflow.sports.step;

import com.solesonic.mcp.service.espn.EspnService;
import com.solesonic.a2a.workflow.framework.WorkflowDecision;
import com.solesonic.a2a.workflow.framework.WorkflowExecutionContext;
import com.solesonic.a2a.workflow.framework.WorkflowStep;
import com.solesonic.a2a.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.a2a.workflow.sports.SportsWorkflowStage;
import com.solesonic.a2a.workflow.sports.model.EspnTeamProfile;
import com.solesonic.a2a.workflow.sports.model.SportsQuestionType;
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

    private final EspnService espnService;

    public FetchEspnStatsStep(EspnService espnService) {
        this.espnService = espnService;
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

        List<SportsQuestionType> questionTypes = context.getSportsQueryIntent() != null
                ? context.getSportsQueryIntent().questionTypes()
                : List.of(SportsQuestionType.GENERAL_NEWS);

        boolean isStatsQuestion = questionTypes.stream()
                .anyMatch(STATS_RELEVANT_TYPES::contains);

        if (!isStatsQuestion) {
            log.info("Skipping stats fetch for question type: {}", questionTypes);
            return WorkflowDecision.skip("Statistics not needed for question type: " + questionTypes);
        }

        context.setCurrentStage(SportsWorkflowStage.FETCHING_ESPN_STATS);
        executionContext.progressTracker().step(name()).update(0.2, "Fetching team statistics from ESPN");

        List<String> teamAbbreviations = resolvedTeams.stream()
                .map(EspnTeamProfile::abbreviation)
                .toList();

        log.info("Fetching ESPN stats via API. Teams: {}", teamAbbreviations);

        try {
            String statsData = espnService.getStatsData(teamAbbreviations);
            context.setEspnStatsData(statsData);
            executionContext.progressTracker().step(name()).done("ESPN stats data fetched");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to fetch ESPN stats data", exception);
            context.setEspnStatsData("ESPN statistics data unavailable.");
            return WorkflowDecision.continueWorkflow();
        }
    }
}
