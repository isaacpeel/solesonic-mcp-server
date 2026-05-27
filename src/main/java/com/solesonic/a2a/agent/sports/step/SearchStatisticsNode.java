package com.solesonic.a2a.agent.sports.step;

import com.solesonic.mcp.model.tavily.TavilySearchRequest;
import com.solesonic.mcp.model.tavily.TavilySearchResponse;
import com.solesonic.mcp.service.tavily.TavilySearchService;
import com.solesonic.a2a.agent.sports.SportsState;
import com.solesonic.a2a.agent.sports.model.SportsQueryIntent;
import com.solesonic.a2a.agent.sports.model.SportsQuestionType;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.mcp.config.tavily.TavilyConstants.DEPTH_ADVANCED;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TOPIC_GENERAL;
import static com.solesonic.a2a.agent.sports.model.SportsQuestionType.GAME_PREVIEW;
import static com.solesonic.a2a.agent.sports.model.SportsQuestionType.PLAYER_ANALYSIS;
import static com.solesonic.a2a.agent.sports.model.SportsQuestionType.STATISTICS;
import static com.solesonic.a2a.agent.sports.model.SportsQuestionType.TRADE_NEWS;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class SearchStatisticsNode implements AsyncNodeAction<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(SearchStatisticsNode.class);

    private static final List<String> STATS_DOMAINS = List.of(
            "basketball-reference.com", "statmuse.com", "espn.com", "nba.com"
    );

    static final Set<SportsQuestionType> APPLICABLE_INTENTS = Set.of(
            GAME_PREVIEW,
            PLAYER_ANALYSIS,
            STATISTICS,
            TRADE_NEWS);

    private final TavilySearchService tavilySearchService;

    public SearchStatisticsNode(TavilySearchService tavilySearchService) {
        this.tavilySearchService = tavilySearchService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state) {
        try {
            SportsQueryIntent sportsQueryIntent = state.sportsQueryIntent().orElseThrow();
            List<SportsQuestionType> questionTypes = sportsQueryIntent.questionTypes();

            if (Collections.disjoint(questionTypes, APPLICABLE_INTENTS)) {
                return completedFuture(Map.of());
            }

            StringBuilder summary = new StringBuilder();
            List<String> statsQueries = buildStatsQueries(sportsQueryIntent);

            for (String query : statsQueries) {
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

                    TavilySearchResponse response = tavilySearchService.search(statsRequest);

                    summary.append("=== Stats: ").append(query).append(" ===\n");
                    summary.append(formatSearchResults(response));
                    summary.append("\n");
                } catch (Exception exception) {
                    log.warn("Statistics search failed for query '{}': {}", query, exception.getMessage());
                    summary.append("=== Stats: ").append(query).append(" ===\nSearch unavailable.\n\n");
                }
            }

            return completedFuture(Map.of(SportsState.STATISTICS_SEARCH_SUMMARY, summary.toString()));
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }

    private List<String> buildStatsQueries(SportsQueryIntent intent) {
        List<String> queries = new ArrayList<>();

        if (intent.hasTeams()) {
            String teamNames = String.join(" vs ", intent.teams());
            queries.add("%sNBA season stats".formatted(teamNames));

            if (intent.teams().size() >= 2) {
                queries.add("%s vs %s head to head recent NBA games".formatted(
                        intent.teams().get(0), intent.teams().get(1)));
            }
        }

        if (intent.hasPlayers()) {
            for (String player : intent.players()) {
                queries.add("%s NBA stats season".formatted(player));
            }
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
