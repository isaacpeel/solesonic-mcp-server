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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.solesonic.mcp.config.tavily.TavilyConstants.*;

@Component
public class SearchSportsNewsStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "search-sports-news";

    private static final Logger log = LoggerFactory.getLogger(SearchSportsNewsStep.class);

    private final TavilySearchService tavilySearchService;

    public SearchSportsNewsStep(TavilySearchService tavilySearchService) {
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
        context.setCurrentStage(SportsWorkflowStage.SEARCHING_NEWS);
        executionContext.progressTracker().step(name()).update(0.1, "Searching for recent news and injury reports");

        SportsQueryIntent intent = context.getSportsQueryIntent();
        String currentMonth = context.getCurrentDateTime().format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        StringBuilder summary = new StringBuilder();

        List<String> newsQueries = buildNewsQueries(intent, currentMonth);

        for (int queryIndex = 0; queryIndex < newsQueries.size(); queryIndex++) {
            String query = newsQueries.get(queryIndex);
            double progressFraction = 0.2 + (0.7 * (queryIndex + 1.0) / newsQueries.size());

            try {
                log.info("Executing sports news search: {}", query);

                TavilySearchRequest newsRequest = TavilySearchRequest.builder()
                        .query(query)
                        .searchDepth(DEPTH_BASIC)
                        .topic(TOPIC_NEWS)
                        .maxResults(5)
                        .includeAnswer(true)
                        .timeRange(TIME_WEEK)
                        .build();

                executionContext.progressTracker().step(name()).update(progressFraction,
                        "Fetching news (%d of %d)".formatted(queryIndex + 1, newsQueries.size()));

                TavilySearchResponse response = tavilySearchService.search(newsRequest);

                summary.append("=== News: ").append(query).append(" ===\n");
                summary.append(formatSearchResults(response));
                summary.append("\n");
            } catch (Exception exception) {
                log.warn("News search failed for query '{}': {}", query, exception.getMessage());
                summary.append("=== News: ").append(query).append(" ===\nSearch unavailable.\n\n");
            }
        }

        context.setNewsSearchSummary(summary.toString());
        executionContext.progressTracker().step(name()).done("News and injury search complete");
        return WorkflowDecision.continueWorkflow();
    }

    private List<String> buildNewsQueries(SportsQueryIntent sportsQueryIntent, String currentMonth) {
        List<String> queries = new ArrayList<>();
        SportsQuestionType questionType = sportsQueryIntent.questionType();

        if (sportsQueryIntent.hasTeams()) {
            String teamNames = String.join(" ", sportsQueryIntent.teams());
            queries.add("%s injuries lineup news %s".formatted(teamNames, currentMonth));

            // Fetch current active roster for question types that analyze specific players
            if (questionType == SportsQuestionType.GAME_PREVIEW || questionType == SportsQuestionType.PLAYER_ANALYSIS) {
                for (String team : sportsQueryIntent.teams()) {
                    queries.add("%s current active roster %s".formatted(team, currentMonth));
                }
            }
        }

        if (sportsQueryIntent.hasPlayers()) {
            // One query per player (capped at 3) so individual status is not diluted in a combined search
            List<String> playersToSearch = sportsQueryIntent.players().size() > 3 ? sportsQueryIntent.players().subList(0, 3) : sportsQueryIntent.players();
            for (String player : playersToSearch) {
                queries.add("%s NBA current team news %s".formatted(player, currentMonth));
            }
        }

        // Fallback if no specific entities were identified
        if (queries.isEmpty()) {
            queries.add("NBA news %s".formatted(currentMonth));
        }

        return queries;
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
