package com.solesonic.agent.sports.node;

import com.solesonic.model.tavily.TavilyExtractResponse;
import com.solesonic.service.tavily.TavilySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NbaComScheduleFetcher {

    private static final Logger log = LoggerFactory.getLogger(NbaComScheduleFetcher.class);

    private static final String NBA_COM_SCHEDULE_URL = "https://www.nba.com/schedule";

    private final TavilySearchService tavilySearchService;

    public NbaComScheduleFetcher(TavilySearchService tavilySearchService) {
        this.tavilySearchService = tavilySearchService;
    }

    public String fetch() {
        log.info("Fetching NBA.com schedule: {}", NBA_COM_SCHEDULE_URL);
        try {
            TavilyExtractResponse response = tavilySearchService.extract(List.of(NBA_COM_SCHEDULE_URL));
            return formatExtractResults(response);
        } catch (Exception exception) {
            log.error("NBA.com schedule extraction failed", exception);
            return null;
        }
    }

    private String formatExtractResults(TavilyExtractResponse response) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (var result : response.results()) {
            if (result.rawContent() != null && !result.rawContent().isBlank()) {
                builder.append("=== NBA.com Schedule: ").append(result.url()).append(" ===\n");
                builder.append(result.rawContent()).append("\n\n");
            }
        }

        return builder.isEmpty() ? null : builder.toString();
    }
}
