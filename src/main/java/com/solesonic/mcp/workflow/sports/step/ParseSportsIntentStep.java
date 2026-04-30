package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.format.DateTimeFormatter;

@Component
public class ParseSportsIntentStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "parse-sports-intent";

    private static final Logger log = LoggerFactory.getLogger(ParseSportsIntentStep.class);

    // Plain String template — avoids ST4 delimiter conflicts with JSON braces
    private static final String PROMPT_TEMPLATE = """
            You are an NBA basketball information assistant. Today's date is %s.

            Analyze the user's NBA question and return a single JSON object with exactly these fields:
              questionType - one of exactly: "SCHEDULE_LOOKUP", "GAME_PREVIEW", "PLAYER_ANALYSIS", "STANDINGS", "GENERAL_NEWS"
              teams        - JSON array of full NBA team names mentioned (e.g. "Boston Celtics" not "Celtics"). Empty array if none.
              players      - JSON array of NBA player names mentioned. Empty array if none.
              sport        - always "basketball"
              league       - always "NBA"
              timeContext  - one of: "today", "upcoming", "recent", "season", or "specific: YYYY-MM-DD"

            Question type guidance:
              SCHEDULE_LOOKUP - user wants to know when a team plays, game time, TV info, or opponent
              GAME_PREVIEW    - user wants analysis of an upcoming game, matchup breakdown, or prediction
              PLAYER_ANALYSIS - user wants detailed info about a specific player's performance or stats
              STANDINGS       - user wants current standings, rankings, win-loss records, or playoff picture
              GENERAL_NEWS    - user wants recent news, injuries, trades, or roster changes

            When normalizing team names, expand common abbreviations and nicknames to full NBA names:
              "Celtics" -> "Boston Celtics", "Lakers" -> "Los Angeles Lakers",
              "Dubs" or "Warriors" -> "Golden State Warriors", "Bucks" -> "Milwaukee Bucks",
              "Cavs" -> "Cleveland Cavaliers", "Knicks" -> "New York Knicks",
              "Heat" -> "Miami Heat", "Nuggets" -> "Denver Nuggets"

            User question: %s

            Return ONLY the JSON object with no explanation or markdown.
            """;

    private final ChatClient chatClient;
    private final JsonMapper jsonMapper;

    public ParseSportsIntentStep(ChatClient chatClient, JsonMapper jsonMapper) {
        this.chatClient = chatClient;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public WorkflowDecision execute(SportsResearchWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(SportsWorkflowStage.PARSING_INTENT);
        executionContext.progressTracker().step(name()).update(0.1, "Analyzing your sports question");

        String formattedDate = context.getCurrentDateTime().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        String promptText = PROMPT_TEMPLATE.formatted(formattedDate, context.getOriginalUserMessage());
        String responseContent = chatClient.prompt().user(promptText).call().content();

        log.debug("Sports intent parse LLM response: {}", responseContent);

        try {
            assert responseContent != null;
            String jsonContent = stripMarkdownCodeFences(responseContent);
            SportsQueryIntent sportsQueryIntent = jsonMapper.readValue(jsonContent, SportsQueryIntent.class);

            log.info("Parsed sports intent: questionType={}, teams={}, players={}, league={}",
                    sportsQueryIntent.questionType(), sportsQueryIntent.teams(),
                    sportsQueryIntent.players(), sportsQueryIntent.league());

            context.setSportsQueryIntent(sportsQueryIntent);

            executionContext.progressTracker().step(name()).done(
                    "Question type: %s, Teams: %s".formatted(
                            sportsQueryIntent.questionType(),
                            sportsQueryIntent.hasTeams() ? String.join(", ", sportsQueryIntent.teams()) : "none"
                    )
            );
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to parse sports intent from LLM response: {}", responseContent, exception);
            return WorkflowDecision.failed("Could not parse sports question intent: " + exception.getMessage());
        }
    }

    private String stripMarkdownCodeFences(String response) {
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        return response.strip();
    }
}
