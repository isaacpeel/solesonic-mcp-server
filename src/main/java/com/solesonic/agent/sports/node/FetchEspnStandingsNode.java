package com.solesonic.agent.sports.node;

import com.solesonic.model.tavily.TavilySearchRequest;
import com.solesonic.model.tavily.TavilySearchResponse;
import com.solesonic.service.espn.EspnService;
import com.solesonic.service.tavily.TavilySearchService;
import com.solesonic.agent.sports.SportsState;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.mcp.config.tavily.TavilyConstants.DEPTH_BASIC;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TOPIC_NEWS;
import static com.solesonic.mcp.prompt.PromptConstants.todayDateOnly;
import static java.util.concurrent.CompletableFuture.completedFuture;

@Component
public class FetchEspnStandingsNode implements AsyncNodeAction<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(FetchEspnStandingsNode.class);

    private final EspnService espnService;
    private final TavilySearchService tavilySearchService;

    public FetchEspnStandingsNode(EspnService espnService, TavilySearchService tavilySearchService) {
        this.espnService = espnService;
        this.tavilySearchService = tavilySearchService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state) {
        try {
            log.info("Fetching ESPN standings");
            String standingsData = espnService.getStandingsData();
            return completedFuture(Map.of(SportsState.STATISTICS_SEARCH_SUMMARY, standingsData));
        } catch (Exception espnException) {
            log.warn("ESPN standings fetch failed — falling back to Tavily: {}", espnException.getMessage());
            return fetchFromTavily();
        }
    }

    private CompletableFuture<Map<String, Object>> fetchFromTavily() {
        try {
            String query = "NBA standings current season " + todayDateOnly();
            log.info("Fetching standings from Tavily: {}", query);

            TavilySearchRequest request = TavilySearchRequest.builder()
                    .query(query)
                    .searchDepth(DEPTH_BASIC)
                    .topic(TOPIC_NEWS)
                    .maxResults(5)
                    .includeAnswer(true)
                    .build();

            TavilySearchResponse response = tavilySearchService.search(request);
            String standingsData = formatTavilyResponse(response);
            return completedFuture(Map.of(SportsState.STATISTICS_SEARCH_SUMMARY, standingsData));
        } catch (Exception tavilyException) {
            log.error("Tavily standings fallback also failed", tavilyException);
            return completedFuture(Map.of(SportsState.STATISTICS_SEARCH_SUMMARY,
                    "Standings data temporarily unavailable. Please check NBA.com for current standings."));
        }
    }

    private String formatTavilyResponse(TavilySearchResponse response) {
        if (response == null) {
            return "No standings data found.";
        }

        StringBuilder builder = new StringBuilder("=== NBA Standings (via Tavily) ===\n");

        if (response.answer() != null && !response.answer().isBlank()) {
            builder.append(response.answer()).append("\n\n");
        }

        if (response.results() != null) {
            for (var result : response.results()) {
                builder.append("Source: ").append(result.title()).append("\n");
                builder.append(result.content()).append("\n\n");
            }
        }

        return builder.toString();
    }
}
