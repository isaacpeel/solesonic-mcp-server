package com.solesonic.agent.sports.node;

import com.solesonic.model.tavily.TavilySearchRequest;
import com.solesonic.model.tavily.TavilySearchResponse;
import com.solesonic.service.tavily.TavilySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.solesonic.mcp.config.tavily.TavilyConstants.*;

@Component
public class TavilyScheduleFetcher {

    private static final Logger log = LoggerFactory.getLogger(TavilyScheduleFetcher.class);

    private final TavilySearchService tavilySearchService;

    public TavilyScheduleFetcher(TavilySearchService tavilySearchService) {
        this.tavilySearchService = tavilySearchService;
    }

    public String fetch(String teamQuery, String currentDate) {
        String query = "%s schedule %s".formatted(teamQuery, currentDate);
        log.info("Running Tavily schedule fallback search: {}", query);

        try {
            TavilySearchRequest request = TavilySearchRequest.builder()
                    .query(query)
                    .searchDepth(DEPTH_BASIC)
                    .topic(TOPIC_GENERAL)
                    .maxResults(5)
                    .includeAnswer(true)
                    .build();

            TavilySearchResponse response = tavilySearchService.search(request);
            return formatSearchResults(response);
        } catch (Exception exception) {
            log.warn("Tavily schedule fallback search failed: {}", exception.getMessage());
            return "Schedule data unavailable.";
        }
    }

    private String formatSearchResults(TavilySearchResponse response) {
        if (response == null) {
            return "No results found.";
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

        return builder.isEmpty() ? "No results found." : builder.toString();
    }
}
