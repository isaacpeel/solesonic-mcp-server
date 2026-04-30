package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;

import static com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext.NBA_TERMINOLOGY;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class SynthesizeSportsAnalysisStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "synthesize-sports-analysis";

    private static final Logger log = LoggerFactory.getLogger(SynthesizeSportsAnalysisStep.class);

    // Plain String template — synthesizes all gathered search results into a final answer.
    // Current date is injected as the first argument to anchor the LLM's temporal reasoning.
    private static final String PROMPT_TEMPLATE = """
            You are a knowledgeable NBA analyst and basketball journalist. Today's date is %s.

            CRITICAL INSTRUCTION: Use the search results below as your PRIMARY and authoritative source
            for all schedule information, game times, scores, rosters, and current news. Do NOT rely
            on your training data for dates, upcoming game times, rosters, player-team associations,
            or recent performance — this information changes frequently and your training data is
            outdated.

            PLAYER ACCURACY RULE: Do NOT mention specific player names unless those players appear
            by name in the search results above. If a player appears in the search results as traded,
            waived, released, or injured long-term, do not reference them as an active contributor
            for the team in question. Never infer roster composition from your training data — only
            name players who are confirmed as currently active in the search results.

            If the search results do not contain enough information to answer the question fully,
            clearly state what is missing and recommend the user check the official team or league
            website directly.

            User question: %s

            Question type: %s

            =============================================
            SCHEDULE SEARCH RESULTS
            =============================================
            %s

            =============================================
            RECENT NEWS AND INJURY REPORTS
            =============================================
            %s

            =============================================
            STATISTICS AND PERFORMANCE DATA
            =============================================
            %s

            =============================================
            NBA TERMINOLOGY
            =============================================
            %s

            =============================================

            Based ONLY on the search results above, provide a comprehensive response. Follow these
            guidelines based on question type:

            SCHEDULE_LOOKUP:
              - State the next upcoming game date, time (include timezone), opponent, and venue
              - Include TV/streaming info if found
              - Be direct and specific — this is the most important detail the user wants
              - If conflicting times appear across sources, note the discrepancy and recommend verification

            GAME_PREVIEW:
              - Summarize both teams' recent form and momentum
              - Highlight key players to watch and their recent performance trends
              - Note any significant injury or lineup news
              - Provide a prediction with clear rationale based on the data
              - Identify potential X-factors or matchup advantages

            PLAYER_ANALYSIS:
              - Present the player's current season statistics
              - Describe their recent game-by-game trend (hot or cold streak?)
              - Contextualize performance relative to their season average
              - Note any injury concerns or role changes
              - Highlight what to watch for in their next game

            STANDINGS:
              - State the current record and division/conference standing
              - Describe the playoff picture and current positioning
              - Note recent winning or losing streaks relevant to the standing

            GENERAL_NEWS:
              - Summarize the most significant recent developments
              - Prioritize injury news, trades, and performance milestones
              - Attribute each key fact to its source

            Always cite the sources you drew from by including the URL where available.
            End with a brief "Sources" section listing all URLs referenced.
            """;

    private final ChatClient chatClient;

    public SynthesizeSportsAnalysisStep(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public WorkflowDecision execute(SportsResearchWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(SportsWorkflowStage.SYNTHESIZING_ANALYSIS);
        executionContext.progressTracker().step(name()).update(0.1, "Synthesizing research into final analysis");

        String currentDate = context.getCurrentDateTime().format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a"));
        SportsQueryIntent intent = context.getSportsQueryIntent();
        String questionType = intent != null ? intent.questionType() : "GENERAL_NEWS";

        String scheduleResults = context.getScheduleSearchSummary() != null
                ? context.getScheduleSearchSummary()
                : "No schedule data gathered.";

        String newsResults = context.getNewsSearchSummary() != null
                ? context.getNewsSearchSummary()
                : "No news data gathered.";

        String statsResults = context.getStatisticsSearchSummary() != null
                ? context.getStatisticsSearchSummary()
                : "Statistics not applicable for this query type.";

        String promptText = PROMPT_TEMPLATE.formatted(
                currentDate,
                context.getOriginalUserMessage(),
                questionType,
                scheduleResults,
                newsResults,
                statsResults,
                NBA_TERMINOLOGY
        );

        log.info("Synthesizing sports analysis for question type: {}", questionType);
        executionContext.progressTracker().step(name()).update(0.5, "Generating analysis");

        try {
            String analysis = chatClient.prompt().user(promptText).call().content();
            context.setFinalAnalysis(analysis);
            log.debug("Sports analysis generated, length: {} chars", analysis != null ? analysis.length() : 0);
            executionContext.progressTracker().step(name()).done("Analysis complete");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to synthesize sports analysis", exception);
            return WorkflowDecision.failed("Could not generate sports analysis: " + exception.getMessage());
        }
    }
}
