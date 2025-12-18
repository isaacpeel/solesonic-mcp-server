package com.solesonic.mcp.service.tavily;

import com.solesonic.mcp.model.tavily.TavilyExtractRequest;
import com.solesonic.mcp.model.tavily.TavilyExtractResponse;
import com.solesonic.mcp.model.tavily.TavilySearchRequest;
import com.solesonic.mcp.model.tavily.TavilySearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static com.solesonic.mcp.config.tavily.TavilyConstants.EXTRACT_ENDPOINT;
import static com.solesonic.mcp.config.tavily.TavilyConstants.SEARCH_ENDPOINT;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TAVILY_API_WEB_CLIENT;

@Service
public class TavilySearchService {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchService.class);

    private final WebClient webClient;

    public TavilySearchService(@Qualifier(TAVILY_API_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    public TavilySearchResponse search(TavilySearchRequest request) {
        log.info("Executing Tavily search for query: {}", request.query());

        TavilySearchResponse response = webClient.post()
                .uri(SEARCH_ENDPOINT)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TavilySearchResponse.class)
                .block();

        log.info("Tavily search completed. Results count: {}",
                response != null && response.results() != null ? response.results().size() : 0);

        return response;
    }

    public TavilyExtractResponse extract(List<String> urls) {
        log.info("Executing Tavily extract for {} URLs", urls.size());

        TavilyExtractRequest request = new TavilyExtractRequest(urls);

        TavilyExtractResponse response = webClient.post()
                .uri(EXTRACT_ENDPOINT)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TavilyExtractResponse.class)
                .block();

        log.info("Tavily extract completed. Success: {}, Failed: {}",
                response != null && response.results() != null ? response.results().size() : 0,
                response != null && response.failedResults() != null ? response.failedResults().size() : 0);

        return response;
    }
}
