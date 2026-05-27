package com.solesonic.a2a.agent.sports.node;

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

import static com.solesonic.mcp.config.tavily.TavilyConstants.DEPTH_BASIC;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TIME_WEEK;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TOPIC_NEWS;
import static com.solesonic.mcp.prompt.PromptConstants.todayDate;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class SearchSportsNewsNode implements AsyncNodeAction<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(SearchSportsNewsNode.class);

    static final Set<SportsQuestionType> APPLICABLE_INTENTS = Set.of(
            SportsQuestionType.GAME_PREVIEW,
            SportsQuestionType.PLAYER_ANALYSIS,
            SportsQuestionType.GENERAL_NEWS,
            SportsQuestionType.TRADE_NEWS);

    private final TavilySearchService tavilySearchService;

    public SearchSportsNewsNode(TavilySearchService tavilySearchService) {
        this.tavilySearchService = tavilySearchService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state) {
        try {
            SportsQueryIntent sportsQueryIntent = state.sportsQueryIntent().orElseThrow();

            if (Collections.disjoint(sportsQueryIntent.questionTypes(), APPLICABLE_INTENTS)) {
                return completedFuture(Map.of());
            }

            String todayDate = todayDate();
            StringBuilder summary = new StringBuilder();
            List<String> newsQueries = buildNewsQueries(sportsQueryIntent, todayDate);

            for (String query : newsQueries) {
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

                    TavilySearchResponse response = tavilySearchService.search(newsRequest);

                    summary.append("=== News: ").append(query).append(" ===\n");
                    summary.append(formatSearchResults(response));
                    summary.append("\n");
                } catch (Exception exception) {
                    log.warn("News search failed for query '{}': {}", query, exception.getMessage());
                    summary.append("=== News: ").append(query).append(" ===\nSearch unavailable.\n\n");
                }
            }

            return completedFuture(Map.of(SportsState.NEWS_SEARCH_SUMMARY, summary.toString()));
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }

    private List<String> buildNewsQueries(SportsQueryIntent sportsQueryIntent, String currentMonth) {
        List<String> queries = new ArrayList<>();
        List<SportsQuestionType> questionTypes = sportsQueryIntent.questionTypes();

        if (sportsQueryIntent.hasTeams()) {
            String teamNames = String.join(" ", sportsQueryIntent.teams());
            queries.add("%s injuries lineup news %s".formatted(teamNames, currentMonth));

            boolean isRosterRelevant = questionTypes.contains(SportsQuestionType.GAME_PREVIEW)
                    || questionTypes.contains(SportsQuestionType.PLAYER_ANALYSIS);
            if (isRosterRelevant) {
                for (String team : sportsQueryIntent.teams()) {
                    queries.add("%s current active roster %s".formatted(team, currentMonth));
                }
            }
        }

        if (sportsQueryIntent.hasPlayers()) {
            List<String> playersToSearch = sportsQueryIntent.players().size() > 3
                    ? sportsQueryIntent.players().subList(0, 3)
                    : sportsQueryIntent.players();
            for (String player : playersToSearch) {
                queries.add("%s NBA current team news %s".formatted(player, currentMonth));
            }
        }

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
