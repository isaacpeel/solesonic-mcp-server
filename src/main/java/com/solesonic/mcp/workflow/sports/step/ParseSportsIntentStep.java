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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.format.DateTimeFormatter;

import static com.solesonic.mcp.workflow.sports.SportsChatClientConfig.SPORTS_CHAT_CLIENT_GPU0;

@Component
public class ParseSportsIntentStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "parse-sports-intent";

    private static final Logger log = LoggerFactory.getLogger(ParseSportsIntentStep.class);

    private static final String PROMPT_TEMPLATE = """
            You are an NBA basketball information assistant. Today's date is %s.

            Analyze the user's NBA question and return a single JSON object with exactly these fields:
              questionType - one of exactly: "SCHEDULE_LOOKUP", "GAME_PREVIEW", "PLAYER_ANALYSIS", \
            "STANDINGS", "TRADE_NEWS", "GENERAL_NEWS"
              teams        - JSON array of full NBA team names mentioned (see normalization table). Empty array if none.
              players      - JSON array of NBA player names mentioned. Empty array if none.
              focusPlayer  - single player name string if the question is specifically about one player; \
            null if the question covers multiple players or no specific player
              sport        - always "basketball"
              league       - always "NBA"
              timeContext  - one of: "today", "upcoming", "recent", "season", or "specific: YYYY-MM-DD"

            Question type guidance:
              SCHEDULE_LOOKUP - user wants to know when a team plays, game time, TV info, or opponent
              GAME_PREVIEW    - user wants analysis of an upcoming game, matchup breakdown, or prediction
              PLAYER_ANALYSIS - user wants detailed info about a specific player's performance or stats
              STANDINGS       - user wants current standings, rankings, win-loss records, or playoff picture
              TRADE_NEWS      - user asks about a specific trade, trade rumor, player transaction, or roster move
              GENERAL_NEWS    - user wants recent news, injuries, or general basketball information

            For timeContext: if the user mentions a specific day or date, resolve it against today's date
            and set timeContext to "specific: YYYY-MM-DD". Examples:
              "tomorrow"      -> "specific: %s"
              "tonight"       -> "specific: %s"
              "Sunday"        -> resolve to the date of the next Sunday
              "next Tuesday"  -> resolve to the date of the next Tuesday

            For focusPlayer: set to the single player name when the question is clearly about one specific
            player (e.g., "How is LeBron playing this season?" -> focusPlayer: "LeBron James"). Set to null
            when the question covers multiple players, a team, or general news.

            Team name normalization — always expand nicknames and abbreviations to the full official name:
              "Hawks"                            -> "Atlanta Hawks"
              "Celtics", "C's"                   -> "Boston Celtics"
              "Nets", "Brooklyn"                 -> "Brooklyn Nets"
              "Hornets"                          -> "Charlotte Hornets"
              "Bulls"                            -> "Chicago Bulls"
              "Cavs", "Cavaliers"                -> "Cleveland Cavaliers"
              "Mavs", "Mavericks"                -> "Dallas Mavericks"
              "Nuggets"                          -> "Denver Nuggets"
              "Pistons"                          -> "Detroit Pistons"
              "Warriors", "Dubs", "GSW"          -> "Golden State Warriors"
              "Rockets"                          -> "Houston Rockets"
              "Pacers"                           -> "Indiana Pacers"
              "Clippers", "LAC"                  -> "Los Angeles Clippers"
              "Lakers", "LAL"                    -> "Los Angeles Lakers"
              "Grizzlies", "Grizz"               -> "Memphis Grizzlies"
              "Heat"                             -> "Miami Heat"
              "Bucks"                            -> "Milwaukee Bucks"
              "Wolves", "Timberwolves", "Minny"  -> "Minnesota Timberwolves"
              "Pelicans"                         -> "New Orleans Pelicans"
              "Knicks"                           -> "New York Knicks"
              "Thunder", "OKC"                   -> "Oklahoma City Thunder"
              "Magic", "Orlando"                 -> "Orlando Magic"
              "Sixers", "76ers", "Philly"        -> "Philadelphia 76ers"
              "Suns"                             -> "Phoenix Suns"
              "Blazers", "Trail Blazers"         -> "Portland Trail Blazers"
              "Kings", "Sacramento"              -> "Sacramento Kings"
              "Spurs"                            -> "San Antonio Spurs"
              "Raptors"                          -> "Toronto Raptors"
              "Jazz"                             -> "Utah Jazz"
              "Wizards"                          -> "Washington Wizards"

            User question: %s

            Return ONLY the JSON object with no explanation or markdown.
            """;

    private final ChatClient chatClient;
    private final JsonMapper jsonMapper;

    public ParseSportsIntentStep(@Qualifier(SPORTS_CHAT_CLIENT_GPU0) ChatClient chatClient,
                                 JsonMapper jsonMapper) {
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
        executionContext.progressTracker().step(name()).update(0.1, "Analyzing your NBA question");

        String todayFormatted = context.getCurrentDateTime().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        String tomorrowFormatted = context.getCurrentDateTime().plusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String todayIso = context.getCurrentDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String promptText = PROMPT_TEMPLATE.formatted(
                todayFormatted,
                tomorrowFormatted,
                todayIso,
                context.getOriginalUserMessage()
        );

        String responseContent = chatClient.prompt().user(promptText).call().content();
        log.debug("Sports intent parse LLM response: {}", responseContent);

        try {
            assert responseContent != null;
            String jsonContent = extractJsonObject(responseContent);
            SportsQueryIntent sportsQueryIntent = jsonMapper.readValue(jsonContent, SportsQueryIntent.class);

            log.info("Parsed sports intent: questionType={}, teams={}, players={}, focusPlayer={}, league={}",
                    sportsQueryIntent.questionType(), sportsQueryIntent.teams(),
                    sportsQueryIntent.players(), sportsQueryIntent.focusPlayer(), sportsQueryIntent.league());

            context.setSportsQueryIntent(sportsQueryIntent);

            executionContext.progressTracker().step(name()).done(
                    "Question type: %s, Teams: %s%s".formatted(
                            sportsQueryIntent.questionType(),
                            sportsQueryIntent.hasTeams() ? String.join(", ", sportsQueryIntent.teams()) : "none",
                            sportsQueryIntent.hasFocusPlayer() ? ", Focus player: " + sportsQueryIntent.focusPlayer() : ""
                    )
            );
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to parse sports intent from LLM response: {}", responseContent, exception);
            return WorkflowDecision.failed("Could not parse sports question intent: " + exception.getMessage());
        }
    }

    private String extractJsonObject(String response) {
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        return response.strip();
    }
}
