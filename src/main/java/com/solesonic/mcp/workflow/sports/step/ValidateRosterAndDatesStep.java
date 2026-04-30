package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;
import com.solesonic.mcp.workflow.sports.model.SportsQuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static com.solesonic.mcp.workflow.sports.SportsChatClientConfig.SPORTS_CHAT_CLIENT_GPU1;

@Component
public class ValidateRosterAndDatesStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "validate-roster-and-dates";

    private static final Logger log = LoggerFactory.getLogger(ValidateRosterAndDatesStep.class);

    private static final String PROMPT_TEMPLATE = """
            You are an NBA roster and schedule analyst. Today is %s.

            The ESPN roster pages below are the AUTHORITATIVE, CURRENT source for player status.
            If a player is listed on an ESPN roster page, they are ACTIVE on that team.
            If a player is NOT listed on any roster page, they are not currently on those teams.

            PLAYERS TO CHECK: %s

            For each player listed above:
              1. Is this player listed by name on any of the ESPN roster pages below? YES or NO
              2. If YES: which team and their position
              3. If NO: check the news data — do any recent articles confirm their current team, \
            trade, waiver, or retirement?

            ESPN ROSTER DATA (authoritative):
            %s

            RECENT NEWS (supplementary):
            %s

            For schedule validation:
              Using the ESPN schedule data below, identify the confirmed NEXT UPCOMING game
              for each team. Flag any game dates that are in the past (before %s).

            ESPN SCHEDULE DATA:
            %s

            Respond using exactly this format:

            PLAYER STATUS:
            [Name | Listed on ESPN Roster | Current Team | Status | Notes]
            (Write "No players to validate" if none were mentioned)

            SCHEDULE VALIDATION:
            [For each team: Next upcoming game — date, time with timezone, opponent, venue]
            (Write "No schedule data" if ESPN schedule data was not fetched)
            """;

    private static final Set<SportsQuestionType> VALIDATION_RELEVANT_TYPES = Set.of(
            SportsQuestionType.GAME_PREVIEW,
            SportsQuestionType.PLAYER_ANALYSIS,
            SportsQuestionType.TRADE_NEWS
    );

    private final ChatClient chatClient;

    public ValidateRosterAndDatesStep(@Qualifier(SPORTS_CHAT_CLIENT_GPU1) ChatClient chatClient) {
        this.chatClient = chatClient;
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

        if (!VALIDATION_RELEVANT_TYPES.contains(questionType)) {
            log.info("Skipping roster validation for question type: {}", questionType);
            return WorkflowDecision.skip("Roster validation not needed for question type: " + questionType);
        }

        context.setCurrentStage(SportsWorkflowStage.VALIDATING_ROSTER);
        executionContext.progressTracker().step(name()).update(0.1, "Validating roster status and schedule dates");

        SportsQueryIntent intent = context.getSportsQueryIntent();
        String todayIso = context.getCurrentDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String playersToCheck = buildPlayersString(intent, context.getFocusPlayerName());

        String rosterData = context.getEspnRosterData();
        String newsData = context.getNewsSearchSummary() != null ? context.getNewsSearchSummary() : "No news data.";
        String scheduleData = context.getEspnScheduleData();

        String promptText = PROMPT_TEMPLATE.formatted(
                todayIso,
                playersToCheck,
                rosterData,
                newsData,
                todayIso,
                scheduleData
        );

        log.info("Validating roster and dates for: {}", playersToCheck);
        executionContext.progressTracker().step(name()).update(0.5, "Cross-referencing ESPN roster and schedule data");

        try {
            String validationResult = chatClient.prompt().user(promptText).call().content();
            log.debug("Roster validation result length: {} chars",
                    validationResult != null ? validationResult.length() : 0);
            context.setRosterValidationSummary(validationResult);
            executionContext.progressTracker().step(name()).done("Roster and schedule validation complete");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to validate roster and dates", exception);
            context.setRosterValidationSummary(
                    "Validation unavailable — treat player and schedule data with caution.");
            return WorkflowDecision.continueWorkflow();
        }
    }

    private String buildPlayersString(SportsQueryIntent intent, String focusPlayerName) {
        if (focusPlayerName != null && !focusPlayerName.isBlank()) {
            return focusPlayerName;
        }
        if (intent != null && intent.hasPlayers()) {
            List<String> players = intent.players();
            return String.join(", ", players.size() > 5 ? players.subList(0, 5) : players);
        }
        return "No specific players mentioned";
    }
}
