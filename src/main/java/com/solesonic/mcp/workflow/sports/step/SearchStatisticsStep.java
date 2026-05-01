package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.model.tavily.TavilySearchRequest;
import com.solesonic.mcp.model.tavily.TavilySearchResponse;
import com.solesonic.mcp.service.tavily.TavilySearchService;
import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;
import com.solesonic.mcp.workflow.sports.model.SportsQuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.solesonic.mcp.config.tavily.TavilyConstants.DEPTH_ADVANCED;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TOPIC_GENERAL;
import static com.solesonic.mcp.workflow.sports.model.SportsQuestionType.*;

@Component
public class SearchStatisticsStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "search-statistics";

    private static final Logger log = LoggerFactory.getLogger(SearchStatisticsStep.class);

    private static final List<String> STATS_DOMAINS = List.of(
            "basketball-reference.com", "statmuse.com", "espn.com", "nba.com"
    );

    private static final Set<SportsQuestionType> NON_STATS_RELEVANT_TYPES = Set.of(SCHEDULE_LOOKUP, GENERAL_NEWS, STANDINGS);

    private final TavilySearchService tavilySearchService;

    public SearchStatisticsStep(TavilySearchService tavilySearchService) {
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
    public WorkflowDecision execute(SportsResearchWorkflowContext sportsResearchWorkflowContext, WorkflowExecutionContext workflowExecutionContext) {
        SportsQueryIntent sportsQueryIntent = sportsResearchWorkflowContext.getSportsQueryIntent();
        List<SportsQuestionType> questionTypes = sportsQueryIntent.questionTypes();

        // Statistics are only meaningful for deep analysis questions

        boolean isNotStatsQuestion = questionTypes.stream()
                .anyMatch(NON_STATS_RELEVANT_TYPES::contains);

        if (isNotStatsQuestion) {
            log.info("Skipping statistics search for question type: {}", questionTypes);
            return WorkflowDecision.skip("Statistics not required for question type: " + questionTypes);
        }

        sportsResearchWorkflowContext.setCurrentStage(SportsWorkflowStage.SEARCHING_STATISTICS);
        workflowExecutionContext.progressTracker().step(name()).update(0.1, "Searching for player and team statistics");

        String seasonString = buildSeasonString(sportsResearchWorkflowContext.getCurrentDateTime());
        StringBuilder summary = new StringBuilder();

        List<String> statsQueries = buildStatsQueries(sportsQueryIntent, seasonString);

        for (int queryIndex = 0; queryIndex < statsQueries.size(); queryIndex++) {
            String query = statsQueries.get(queryIndex);
            double progressFraction = 0.2 + (0.7 * (queryIndex + 1.0) / statsQueries.size());

            try {
                log.info("Executing statistics search: {}", query);

                TavilySearchRequest statsRequest = TavilySearchRequest.builder()
                        .query(query)
                        .searchDepth(DEPTH_ADVANCED)
                        .topic(TOPIC_GENERAL)
                        .maxResults(5)
                        .includeAnswer(true)
                        .includeDomains(STATS_DOMAINS)
                        .build();

                workflowExecutionContext.progressTracker().step(name()).update(progressFraction,
                        "Fetching statistics (%d of %d)".formatted(queryIndex + 1, statsQueries.size()));

                TavilySearchResponse response = tavilySearchService.search(statsRequest);

                summary.append("=== Stats: ").append(query).append(" ===\n");
                summary.append(formatSearchResults(response));
                summary.append("\n");
            } catch (Exception exception) {
                log.warn("Statistics search failed for query '{}': {}", query, exception.getMessage());
                summary.append("=== Stats: ").append(query).append(" ===\nSearch unavailable.\n\n");
            }
        }

        sportsResearchWorkflowContext.setStatisticsSearchSummary(summary.toString());
        workflowExecutionContext.progressTracker().step(name()).done("Statistics search complete");
        return WorkflowDecision.continueWorkflow();
    }

    private List<String> buildStatsQueries(SportsQueryIntent intent, String seasonString) {
        List<String> queries = new ArrayList<>();

        // Team season stats
        if (intent.hasTeams()) {
            String teamNames = String.join(" vs ", intent.teams());
            queries.add("%s %s NBA season stats".formatted(teamNames, seasonString));

            // Head-to-head if two teams are present (game preview)
            if (intent.teams().size() >= 2) {
                queries.add("%s vs %s head to head recent NBA games".formatted(
                        intent.teams().get(0), intent.teams().get(1)));
            }
        }

        // Player stats
        if (intent.hasPlayers()) {
            for (String player : intent.players()) {
                queries.add("%s NBA stats %s season".formatted(player, seasonString));
            }
        }

        return queries;
    }

    private String buildSeasonString(LocalDateTime currentDateTime) {
        int year = currentDateTime.getYear();
        int month = currentDateTime.getMonthValue();
        // NBA season runs October – June. July–September is the off-season.
        if (month >= 7) {
            return year + "-" + (year + 1);
        } else {
            return (year - 1) + "-" + year;
        }
    }

    private String formatSearchResults(TavilySearchResponse response) {
        if (response == null) {
            return "No results found.\n";
        }

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

        return builder.isEmpty() ? "No results found.\n" : builder.toString();
    }
}
