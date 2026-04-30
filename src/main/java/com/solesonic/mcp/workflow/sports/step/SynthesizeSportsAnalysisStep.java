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

import java.time.format.DateTimeFormatter;

import static com.solesonic.mcp.workflow.sports.SportsChatClientConfig.SPORTS_CHAT_CLIENT_GPU0;
import static com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext.NBA_TERMINOLOGY;

@Component
public class SynthesizeSportsAnalysisStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "synthesize-sports-analysis";

    private static final Logger log = LoggerFactory.getLogger(SynthesizeSportsAnalysisStep.class);

    private static final String PROMPT_TEMPLATE = """
            You are a professional NBA analyst and basketball journalist. Today is %s.

            CRITICAL — DATA AUTHORITY HIERARCHY:
              1. ESPN ROSTER DATA is the ground truth for which players are on which teams.
                 A player listed on ESPN's roster page IS on that team. A player not listed IS NOT.
              2. ESPN SCHEDULE DATA is the ground truth for game dates and times.
                 Use exact dates and times as shown — do not calculate or infer schedule.
              3. ESPN STATISTICS DATA is the authoritative source for team and player performance metrics.
              4. NEWS DATA supplements the above — use for context, injuries, and recent developments.
              5. Do NOT use your training data for player-team associations, roster composition,
                 game dates, or recent performance. Your training data is outdated.

            PLAYER ACCURACY RULE:
              Only name a player as an active contributor to a team if they appear by name in the
              ESPN ROSTER DATA for that team. If the VALIDATED ROSTER STATUS section marks them as
              NOT on the ESPN roster, do not treat them as active regardless of training data.

            DATE FORMAT RULE:
              Always express game times as: Day-of-week, YYYY-MM-DD, H:MM AM/PM TZ
              Example: Wednesday, 2026-04-30, 7:30 PM ET

            If the data is insufficient to fully answer the question, state clearly what is missing
            and direct the user to ESPN.com for the latest information.

            ─────────────────────────────────────────────
            USER QUESTION: %s
            QUESTION TYPE: %s
            ─────────────────────────────────────────────

            ═══════════════════════════════════════════
            VALIDATED ROSTER AND SCHEDULE STATUS
            (highest authority — cross-references ESPN data against players mentioned)
            ═══════════════════════════════════════════
            %s

            ═══════════════════════════════════════════
            ESPN SCHEDULE DATA  (official game schedule)
            Source: espn.com/nba/team/schedule
            ═══════════════════════════════════════════
            %s

            ═══════════════════════════════════════════
            ESPN ROSTER DATA  (current official roster)
            Source: espn.com/nba/team/roster
            ═══════════════════════════════════════════
            %s

            ═══════════════════════════════════════════
            ESPN STATISTICS DATA  (official team stats)
            Source: espn.com/nba/team/stats
            ═══════════════════════════════════════════
            %s

            ═══════════════════════════════════════════
            ESPN STANDINGS DATA
            Source: espn.com/nba/standings
            ═══════════════════════════════════════════
            %s

            ═══════════════════════════════════════════
            RECENT NEWS, INJURIES AND TRANSACTIONS
            Source: news search + espn.com/nba/transactions
            ═══════════════════════════════════════════
            %s

            ═══════════════════════════════════════════
            DEEP PLAYER ANALYSIS
            ═══════════════════════════════════════════
            %s

            ═══════════════════════════════════════════
            NBA TERMINOLOGY REFERENCE
            ═══════════════════════════════════════════
            %s

            ─────────────────────────────────────────────
            Based ONLY on the data above, provide a comprehensive response per question type:

            SCHEDULE_LOOKUP:
              - State the next game: Day, YYYY-MM-DD, tipoff time with timezone, opponent, venue
              - TV/streaming info if present in the schedule data
              - Flag any conflicting times and recommend espn.com verification

            GAME_PREVIEW:
              - Both teams' recent form from the schedule data (last 5+ games)
              - Key active players confirmed in ESPN roster data; only name those listed
              - All injury and lineup news from the news section
              - Head-to-head context if present in the data
              - Analytical prediction grounded in the statistics and form data
              - Matchup advantages and X-factors

            PLAYER_ANALYSIS:
              - Use the deep player analysis section as the primary source
              - Season averages, recent form trend, role, impact metrics
              - Confirmed injury status from the validated section
              - What to watch in the next game

            STANDINGS:
              - Current record and conference standing from the ESPN standings data
              - Playoff seeding, games ahead/behind key positions
              - Clinching or elimination scenarios if relevant
              - Recent form trend and trajectory

            TRADE_NEWS:
              - Players and teams involved — confirmed from ESPN transactions and roster data
              - Assets exchanged (players, picks, cash) — attribute to source
              - Mark as official vs. reported/rumored
              - Roster impact on both teams

            GENERAL_NEWS:
              - Most significant recent developments prioritized by impact
              - Injury updates, roster moves, performance milestones
              - Each fact attributed to its source

            End with a "Sources" section listing all ESPN URLs and news URLs referenced.
            """;

    private final ChatClient chatClient;

    public SynthesizeSportsAnalysisStep(@Qualifier(SPORTS_CHAT_CLIENT_GPU0) ChatClient chatClient) {
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

        String currentDate = context.getCurrentDateTime()
                .format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd"));
        SportsQueryIntent intent = context.getSportsQueryIntent();
        String questionType = intent != null ? intent.questionType() : "GENERAL_NEWS";

        String deepPlayerSection = context.getDeepPlayerAnalysisSummary() != null
                ? context.getDeepPlayerAnalysisSummary()
                : "No focused player analysis for this query type.";

        String promptText = PROMPT_TEMPLATE.formatted(
                currentDate,
                context.getOriginalUserMessage(),
                questionType,
                context.getRosterValidationSummary(),
                context.getEspnScheduleData(),
                context.getEspnRosterData(),
                context.getEspnStatsData(),
                context.getEspnStandingsData(),
                context.getNewsSearchSummary() != null ? context.getNewsSearchSummary() : "No news data.",
                deepPlayerSection,
                NBA_TERMINOLOGY
        );

        log.info("Synthesizing NBA analysis for question type: {}", questionType);
        executionContext.progressTracker().step(name()).update(0.5, "Generating analysis");

        try {
            String analysis = chatClient.prompt().user(promptText).call().content();
            context.setFinalAnalysis(analysis);
            log.debug("Analysis generated: {} chars", analysis != null ? analysis.length() : 0);
            executionContext.progressTracker().step(name()).done("Analysis complete");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to synthesize NBA analysis", exception);
            return WorkflowDecision.failed("Could not generate NBA analysis: " + exception.getMessage());
        }
    }
}
