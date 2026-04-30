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

import static com.solesonic.mcp.config.tavily.TavilyConstants.*;

@Component
public class SearchStatisticsStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "search-statistics";

    private static final Logger log = LoggerFactory.getLogger(SearchStatisticsStep.class);

    private static final List<String> STATS_DOMAINS = List.of(
            "basketball-reference.com", "baseball-reference.com", "pro-football-reference.com",
            "hockey-reference.com", "statmuse.com", "espn.com"
    );

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
    public WorkflowDecision execute(SportsResearchWorkflowContext context, WorkflowExecutionContext executionContext) {
        SportsQueryIntent intent = context.getSportsQueryIntent();
        SportsQuestionType questionType = intent != null ? intent.resolvedQuestionType() : SportsQuestionType.GENERAL_NEWS;

        // Statistics are only meaningful for deep analysis questions
        if (questionType == SportsQuestionType.SCHEDULE_LOOKUP
                || questionType == SportsQuestionType.STANDINGS
                || questionType == SportsQuestionType.GENERAL_NEWS) {
            log.info("Skipping statistics search for question type: {}", questionType);
            return WorkflowDecision.skip("Statistics not required for question type: " + questionType);
        }

        context.setCurrentStage(SportsWorkflowStage.SEARCHING_STATISTICS);
        executionContext.progressTracker().step(name()).update(0.1, "Searching for player and team statistics");

        String league = (intent.league() != null && !intent.league().isBlank()) ? intent.league() : "";
        String seasonString = buildSeasonString(intent, context.getCurrentDateTime());
        StringBuilder summary = new StringBuilder();

        List<String> statsQueries = buildStatsQueries(intent, league, seasonString);

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

                executionContext.progressTracker().step(name()).update(progressFraction,
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

        context.setStatisticsSearchSummary(summary.toString());
        executionContext.progressTracker().step(name()).done("Statistics search complete");
        return WorkflowDecision.continueWorkflow();
    }

    private List<String> buildStatsQueries(SportsQueryIntent intent, String league, String seasonString) {
        List<String> queries = new ArrayList<>();

        // Team season stats
        if (intent.hasTeams()) {
            String teamNames = String.join(" vs ", intent.teams());
            queries.add("%s %s season stats %s".formatted(teamNames, seasonString, league).strip());

            // Head-to-head if two teams are present (game preview)
            if (intent.teams().size() >= 2) {
                queries.add("%s vs %s head to head recent games".formatted(
                        intent.teams().get(0), intent.teams().get(1)));
            }
        }

        // Player stats
        if (intent.hasPlayers()) {
            for (String player : intent.players()) {
                queries.add("%s stats %s season %s".formatted(player, seasonString, league).strip());
            }
        }

        return queries;
    }

    private String buildSeasonString(SportsQueryIntent intent, LocalDateTime currentDateTime) {
        int year = currentDateTime.getYear();
        int month = currentDateTime.getMonthValue();
        String league = intent.league() != null ? intent.league().toUpperCase() : "";

        return switch (league) {
            // NFL season year is the year of the September kickoff
            case "NFL" -> month >= 7 ? String.valueOf(year) : String.valueOf(year - 1);
            // MLB and MLS run within a single calendar year
            case "MLB", "MLS" -> String.valueOf(year);
            default -> {
                // NBA, NHL, and unknown: cross-calendar-year seasons (Oct – Jun)
                // July–September is the off-season; point to the upcoming season
                if (month >= 7) {
                    yield year + "-" + (year + 1);
                } else {
                    // January–June: mid-season that started the prior year
                    yield (year - 1) + "-" + year;
                }
            }
        };
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
