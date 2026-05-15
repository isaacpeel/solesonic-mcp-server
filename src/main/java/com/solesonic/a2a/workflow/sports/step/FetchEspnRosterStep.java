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
public class FetchEspnRosterStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "fetch-espn-roster";

    private static final Logger log = LoggerFactory.getLogger(FetchEspnRosterStep.class);

    private static final Set<SportsQuestionType> ROSTER_RELEVANT_TYPES = Set.of(
            SportsQuestionType.GAME_PREVIEW,
            SportsQuestionType.PLAYER_ANALYSIS,
            SportsQuestionType.TRADE_NEWS
    );

    private final EspnService espnService;

    public FetchEspnRosterStep(EspnService espnService) {
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
            log.info("No resolved teams — skipping ESPN roster fetch");
            return WorkflowDecision.skip("No teams resolved for roster fetch");
        }

        List<SportsQuestionType> questionTypes = context.getSportsQueryIntent() != null
                ? context.getSportsQueryIntent().questionTypes()
                : List.of(SportsQuestionType.GENERAL_NEWS);

        boolean isRosterQuestion = questionTypes.stream()
                .anyMatch(ROSTER_RELEVANT_TYPES::contains);

        if (!isRosterQuestion) {
            log.info("Skipping roster fetch for question type: {}", questionTypes);
            return WorkflowDecision.skip("Roster not needed for question type: " + questionTypes);
        }

        context.setCurrentStage(SportsWorkflowStage.FETCHING_ESPN_ROSTER);
        executionContext.progressTracker().step(name()).update(0.2, "Fetching current rosters from ESPN");

        List<String> teamAbbreviations = resolvedTeams.stream()
                .map(EspnTeamProfile::abbreviation)
                .toList();

        log.info("Fetching ESPN roster via API. Teams: {}", teamAbbreviations);

        try {
            String rosterData = espnService.getRosterData(teamAbbreviations);
            context.setEspnRosterData(rosterData);
            executionContext.progressTracker().step(name()).done("ESPN roster data fetched");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to fetch ESPN roster data", exception);
            context.setEspnRosterData("ESPN roster data unavailable.");
            return WorkflowDecision.continueWorkflow();
        }
    }
}
