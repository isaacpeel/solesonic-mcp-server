package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.model.tavily.TavilySearchRequest;
import com.solesonic.mcp.model.tavily.TavilySearchResponse;
import com.solesonic.mcp.service.tavily.TavilySearchService;
import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.SportsQuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.solesonic.mcp.config.tavily.TavilyConstants.*;
import static com.solesonic.mcp.workflow.sports.SportsChatClientConfig.SPORTS_CHAT_CLIENT_GPU1;

@Component
public class DeepPlayerAnalysisStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "deep-player-analysis";

    private static final Logger log = LoggerFactory.getLogger(DeepPlayerAnalysisStep.class);

    private static final List<String> PLAYER_STATS_DOMAINS = List.of(
            "basketball-reference.com", "statmuse.com", "espn.com"
    );

    private static final String PROMPT_TEMPLATE = """
            You are an NBA advanced analytics journalist. Today is %s.

            Produce a comprehensive deep-dive analysis of %s using ONLY the data in the search results
            and ESPN data provided below. Do NOT infer statistics or facts from your training data.
            If a specific metric is not present in the provided data, state "data not found."

            ANALYSIS REQUIREMENTS:

            1. Season Overview
               Per-game averages: points, rebounds, assists, steals, blocks, turnovers,
               FG%%, 3P%%, FT%%, TS%%, minutes. Only cite figures present in the data.

            2. Recent Form (Last 10 Games)
               Game-by-game scoring trend. Is production improving, declining, or consistent?
               Identify any standout performances or concerning stretches.

            3. Role and Usage
               Starter or bench? Usage rate, minutes per game. Any role changes this season?

            4. Impact Metrics
               Plus/minus, net rating, on/off court differentials from the data.

            5. Injury and Availability
               Current confirmed status and any injury history affecting this season.

            6. Matchup Outlook
               Based on upcoming opponent and any head-to-head history in the data.

            7. Career Context
               How this season compares to career averages (if that data is present).

            8. Contract and Situation
               Any contract details that explain their current role (if present in data).

            === ESPN TEAM STATS DATA ===
            %s

            === PLAYER SEARCH RESULTS (basketball-reference, statmuse, ESPN) ===
            %s
            """;

    private final ChatClient chatClient;
    private final TavilySearchService tavilySearchService;

    public DeepPlayerAnalysisStep(@Qualifier(SPORTS_CHAT_CLIENT_GPU1) ChatClient chatClient,
                                  TavilySearchService tavilySearchService) {
        this.chatClient = chatClient;
        this.tavilySearchService = tavilySearchService;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public WorkflowDecision execute(SportsResearchWorkflowContext context, WorkflowExecutionContext executionContext) {
        String playerName = resolvePlayerName(context);
        if (playerName == null) {
            return WorkflowDecision.skip("Deep player analysis requires a specific player focus");
        }

        SportsQuestionType questionType = context.getSportsQueryIntent() != null
                ? context.getSportsQueryIntent().resolvedQuestionType()
                : SportsQuestionType.GENERAL_NEWS;

        if (questionType != SportsQuestionType.PLAYER_ANALYSIS) {
            return WorkflowDecision.skip("Deep player analysis only runs for PLAYER_ANALYSIS queries");
        }

        context.setCurrentStage(SportsWorkflowStage.ANALYZING_PLAYER);
        executionContext.progressTracker().step(name()).update(0.1, "Gathering deep analytics for " + playerName);

        String playerSearchResults = runPlayerSearches(context, executionContext, playerName);

        String todayIso = context.getCurrentDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String espnStats = context.getEspnStatsData() != null ? context.getEspnStatsData() : "No team stats available.";

        String promptText = PROMPT_TEMPLATE.formatted(todayIso, playerName, espnStats, playerSearchResults);

        log.info("Generating deep player analysis for: {}", playerName);
        executionContext.progressTracker().step(name()).update(0.8, "Synthesizing player analysis");

        try {
            String analysis = chatClient.prompt().user(promptText).call().content();
            context.setDeepPlayerAnalysisSummary(analysis);
            executionContext.progressTracker().step(name()).done("Deep player analysis complete");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to generate deep player analysis for {}", playerName, exception);
            context.setDeepPlayerAnalysisSummary("Deep player analysis unavailable.");
            return WorkflowDecision.continueWorkflow();
        }
    }

    private String runPlayerSearches(SportsResearchWorkflowContext context,
                                     WorkflowExecutionContext executionContext,
                                     String playerName) {
        String currentDate = context.getCurrentDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<String> queries = List.of(
                "%s NBA stats this season per game averages".formatted(playerName),
                "%s recent game log results".formatted(playerName),
                "%s NBA injury status %s".formatted(playerName, currentDate)
        );

        StringBuilder results = new StringBuilder();
        for (int queryIndex = 0; queryIndex < queries.size(); queryIndex++) {
            String query = queries.get(queryIndex);
            double progressFraction = 0.2 + (0.5 * (queryIndex + 1.0) / queries.size());

            try {
                log.info("Executing player deep-dive search: {}", query);

                TavilySearchRequest playerRequest = TavilySearchRequest.builder()
                        .query(query)
                        .searchDepth(DEPTH_ADVANCED)
                        .topic(TOPIC_GENERAL)
                        .maxResults(10)
                        .includeAnswer(true)
                        .includeDomains(PLAYER_STATS_DOMAINS)
                        .build();

                executionContext.progressTracker().step(name()).update(progressFraction,
                        "Fetching player data (%d of %d)".formatted(queryIndex + 1, queries.size()));

                TavilySearchResponse response = tavilySearchService.search(playerRequest);
                results.append("=== ").append(query).append(" ===\n");
                results.append(formatSearchResults(response)).append("\n");

            } catch (Exception exception) {
                log.warn("Player search failed for '{}': {}", query, exception.getMessage());
            }
        }

        return results.isEmpty() ? "No player search results available." : results.toString();
    }

    private String resolvePlayerName(SportsResearchWorkflowContext context) {
        if (context.getFocusPlayerName() != null && !context.getFocusPlayerName().isBlank()) {
            return context.getFocusPlayerName();
        }
        if (context.getSportsQueryIntent() != null && context.getSportsQueryIntent().hasFocusPlayer()) {
            return context.getSportsQueryIntent().focusPlayer();
        }
        return null;
    }

    private String formatSearchResults(TavilySearchResponse response) {
        if (response == null) return "No results.\n";
        StringBuilder builder = new StringBuilder();
        if (response.answer() != null && !response.answer().isBlank()) {
            builder.append("Summary: ").append(response.answer()).append("\n\n");
        }
        if (response.results() != null) {
            for (var result : response.results()) {
                builder.append("Title: ").append(result.title()).append("\n");
                builder.append("URL: ").append(result.url()).append("\n");
                builder.append("Content: ").append(result.content()).append("\n\n");
            }
        }
        return builder.isEmpty() ? "No results.\n" : builder.toString();
    }
}
