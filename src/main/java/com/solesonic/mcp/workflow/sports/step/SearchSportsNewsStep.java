package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.model.tavily.TavilyExtractResponse;
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

    private static final String ESPN_TRANSACTIONS_URL = "https://www.espn.com/nba/transactions";

    private static final List<String> TRADE_DOMAINS = List.of(
            "spotrac.com", "hoopshype.com", "espn.com", "nba.com"
    );

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
        SportsQueryIntent intent = context.getSportsQueryIntent();
        SportsQuestionType questionType = intent != null
                ? intent.resolvedQuestionType()
                : SportsQuestionType.GENERAL_NEWS;

        if (questionType == SportsQuestionType.SCHEDULE_LOOKUP) {
            log.info("Skipping news search for SCHEDULE_LOOKUP — schedule data is sufficient");
            return WorkflowDecision.skip("News search not needed for schedule lookup");
        }

        context.setCurrentStage(SportsWorkflowStage.SEARCHING_NEWS);
        executionContext.progressTracker().step(name()).update(0.1, "Searching for injury reports and news");

        String currentDate = context.getCurrentDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE);

        StringBuilder summary = new StringBuilder();

        if (questionType == SportsQuestionType.TRADE_NEWS) {
            fetchEspnTransactions(summary, executionContext);
        }

        List<String> newsQueries = buildNewsQueries(intent, questionType, currentDate);
        for (int queryIndex = 0; queryIndex < newsQueries.size(); queryIndex++) {
            String query = newsQueries.get(queryIndex);
            double progressFraction = 0.3 + (0.6 * (queryIndex + 1.0) / newsQueries.size());

            try {
                log.info("Executing news search: {}", query);

                TavilySearchRequest.Builder requestBuilder = TavilySearchRequest.builder()
                        .query(query)
                        .searchDepth(DEPTH_BASIC)
                        .topic(TOPIC_NEWS)
                        .maxResults(5)
                        .includeAnswer(true)
                        .timeRange(TIME_WEEK);

                if (questionType == SportsQuestionType.TRADE_NEWS) {
                    requestBuilder.includeDomains(TRADE_DOMAINS);
                }

                executionContext.progressTracker().step(name()).update(progressFraction,
                        "Fetching news (%d of %d)".formatted(queryIndex + 1, newsQueries.size()));

                TavilySearchResponse response = tavilySearchService.search(requestBuilder.build());
                summary.append("=== ").append(query).append(" ===\n");
                summary.append(formatSearchResults(response));
                summary.append("\n");

            } catch (Exception exception) {
                log.warn("News search failed for query '{}': {}", query, exception.getMessage());
                summary.append("=== ").append(query).append(" ===\nSearch unavailable.\n\n");
            }
        }

        context.setNewsSearchSummary(summary.toString());
        executionContext.progressTracker().step(name()).done("News search complete");
        return WorkflowDecision.continueWorkflow();
    }

    private void fetchEspnTransactions(StringBuilder summary, WorkflowExecutionContext executionContext) {
        try {
            log.info("Fetching ESPN transactions page for TRADE_NEWS query");
            executionContext.progressTracker().step(name()).update(0.2, "Fetching ESPN transactions");
            TavilyExtractResponse transactionsResponse = tavilySearchService.extract(List.of(ESPN_TRANSACTIONS_URL));
            summary.append("=== ESPN Transactions (Official) ===\n");
            if (transactionsResponse != null && transactionsResponse.results() != null) {
                for (var result : transactionsResponse.results()) {
                    summary.append(result.rawContent()).append("\n");
                }
            }
            summary.append("\n");
        } catch (Exception exception) {
            log.warn("ESPN transactions fetch failed: {}", exception.getMessage());
            summary.append("=== ESPN Transactions ===\nUnavailable.\n\n");
        }
    }

    private List<String> buildNewsQueries(SportsQueryIntent intent, SportsQuestionType questionType, String currentDate) {
        List<String> queries = new ArrayList<>();

        if (intent != null && intent.hasTeams()) {
            String teamsString = String.join(" ", intent.teams());
            queries.add("%s injuries lineup availability %s".formatted(teamsString, currentDate));

            if (questionType == SportsQuestionType.TRADE_NEWS) {
                queries.add("%s trade transaction news %s".formatted(teamsString, currentDate));
            }
        }

        if (intent != null && intent.hasPlayers()) {
            List<String> players = intent.players();
            int playerLimit = Math.min(players.size(), 5);
            for (String player : players.subList(0, playerLimit)) {
                queries.add("%s NBA news %s".formatted(player, currentDate));
            }
        }

        if (queries.isEmpty()) {
            queries.add("NBA basketball news %s".formatted(currentDate));
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
