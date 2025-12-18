package com.solesonic.mcp.tool.tavily;

import com.solesonic.mcp.model.tavily.TavilyExtractResponse;
import com.solesonic.mcp.model.tavily.TavilySearchRequest;
import com.solesonic.mcp.model.tavily.TavilySearchResponse;
import com.solesonic.mcp.service.tavily.TavilySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.solesonic.mcp.config.tavily.TavilyConstants.DEPTH_ADVANCED;
import static com.solesonic.mcp.config.tavily.TavilyConstants.DEPTH_BASIC;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TIME_DAY;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TIME_MONTH;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TIME_WEEK;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TIME_YEAR;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TOPIC_GENERAL;
import static com.solesonic.mcp.config.tavily.TavilyConstants.TOPIC_NEWS;

@SuppressWarnings("unused")
@Service
public class WebSearchTools {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);

    public static final String WEB_SEARCH = "web_search";
    public static final String WEB_SEARCH_ADVANCED = "web_search_advanced";
    public static final String WEB_SEARCH_NEWS = "web_search_news";
    public static final String WEB_EXTRACT_CONTENT = "web_extract_content";

    public static final String WEB_SEARCH_DESC = """
            Performs a general web search and returns relevant results with summaries.
            Use this tool to find information on any topic from the web.
            Returns a direct answer (if available), along with a list of relevant web pages with titles, URLs, and content snippets.
            """;

    public static final String WEB_SEARCH_ADVANCED_DESC = """
            Performs an advanced web search with deeper relevance analysis.
            Use this for complex queries requiring more thorough research.
            This search takes longer but returns more accurate and relevant results.
            Supports filtering by domains and time range.
            """;

    public static final String WEB_SEARCH_NEWS_DESC = """
            Searches recent news articles on a given topic.
            Optimized for finding current events and recent news coverage.
            Supports time filtering (day, week, month, year).
            """;

    public static final String WEB_EXTRACT_DESC = """
            Extracts and retrieves the full content from one or more web page URLs.
            Use this when you need the complete text content from specific URLs discovered through search.
            Maximum 5 URLs per request recommended.
            """;

    private final TavilySearchService tavilySearchService;

    public WebSearchTools(TavilySearchService tavilySearchService) {
        this.tavilySearchService = tavilySearchService;
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-WEB-SEARCH')")
    @McpTool(name = WEB_SEARCH, description = WEB_SEARCH_DESC)
    public WebSearchResponse webSearch(
            @McpToolParam(description = "The search query. Be specific and include relevant keywords for best results.")
            String query,
            @McpToolParam(description = "Maximum number of results to return (1-10, default 5)")
            Integer maxResults
    ) {
        log.info("Executing basic web search for: {}", query);

        int resultCount = (maxResults != null && maxResults >= 1 && maxResults <= 10) ? maxResults : 5;

        TavilySearchRequest request = TavilySearchRequest.builder()
                .query(query)
                .searchDepth(DEPTH_BASIC)
                .topic(TOPIC_GENERAL)
                .maxResults(resultCount)
                .includeAnswer(true)
                .includeRawContent(false)
                .includeImages(false)
                .build();

        TavilySearchResponse response = tavilySearchService.search(request);

        return mapToWebSearchResponse(response);
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-WEB-SEARCH')")
    @McpTool(name = WEB_SEARCH_ADVANCED, description = WEB_SEARCH_ADVANCED_DESC)
    public WebSearchResponse webSearchAdvanced(
            @McpToolParam(description = "The search query. Be specific and include relevant keywords.")
            String query,
            @McpToolParam(description = "Maximum number of results to return (1-20, default 10)")
            Integer maxResults,
            @McpToolParam(description = "List of domains to include (e.g., ['github.com', 'stackoverflow.com']). Optional.")
            List<String> includeDomains,
            @McpToolParam(description = "List of domains to exclude. Optional.")
            List<String> excludeDomains,
            @McpToolParam(description = "Time range filter: 'day', 'week', 'month', or 'year'. Optional.")
            String timeRange,
            @McpToolParam(description = "Whether to include raw page content (increases response size). Default false.")
            Boolean includeRawContent
    ) {
        log.info("Executing advanced web search for: {}", query);

        int resultCount = (maxResults != null && maxResults >= 1 && maxResults <= 20) ? maxResults : 10;
        boolean rawContent = includeRawContent != null && includeRawContent;

        TavilySearchRequest request = TavilySearchRequest.builder()
                .query(query)
                .searchDepth(DEPTH_ADVANCED)
                .topic(TOPIC_GENERAL)
                .maxResults(resultCount)
                .includeAnswer(true)
                .includeRawContent(rawContent)
                .includeImages(false)
                .includeDomains(includeDomains)
                .excludeDomains(excludeDomains)
                .timeRange(timeRange)
                .build();

        TavilySearchResponse response = tavilySearchService.search(request);

        return mapToWebSearchResponse(response);
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-WEB-SEARCH')")
    @McpTool(name = WEB_SEARCH_NEWS, description = WEB_SEARCH_NEWS_DESC)
    public WebSearchResponse webSearchNews(
            @McpToolParam(description = "The news search query. Include topic, keywords, or entities.")
            String query,
            @McpToolParam(description = "Maximum number of results (1-10, default 5)")
            Integer maxResults,
            @McpToolParam(description = "Time range: 'day' (24h), 'week', 'month', or 'year'. Default is 'week'.")
            String timeRange
    ) {
        log.info("Executing news search for: {}", query);

        int resultCount = (maxResults != null && maxResults >= 1 && maxResults <= 10) ? maxResults : 5;
        String range = (timeRange != null && List.of(TIME_DAY, TIME_WEEK, TIME_MONTH, TIME_YEAR).contains(timeRange))
                ? timeRange : TIME_WEEK;

        TavilySearchRequest request = TavilySearchRequest.builder()
                .query(query)
                .searchDepth(DEPTH_BASIC)
                .topic(TOPIC_NEWS)
                .maxResults(resultCount)
                .includeAnswer(true)
                .timeRange(range)
                .build();

        TavilySearchResponse response = tavilySearchService.search(request);

        return mapToWebSearchResponse(response);
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-WEB-SEARCH')")
    @McpTool(name = WEB_EXTRACT_CONTENT, description = WEB_EXTRACT_DESC)
    public WebExtractResponse webExtractContent(
            @McpToolParam(description = "List of URLs to extract content from. Maximum 5 URLs recommended.")
            List<String> urls
    ) {
        log.info("Executing content extraction for {} URLs", urls != null ? urls.size() : 0);

        if (urls == null || urls.isEmpty()) {
            return new WebExtractResponse(List.of(), List.of("No URLs provided"));
        }

        List<String> limitedUrls = urls.size() > 5 ? urls.subList(0, 5) : urls;

        TavilyExtractResponse response = tavilySearchService.extract(limitedUrls);

        return mapToWebExtractResponse(response);
    }

    private WebSearchResponse mapToWebSearchResponse(TavilySearchResponse response) {
        if (response == null) {
            return new WebSearchResponse(null, List.of(), null);
        }

        List<WebSearchResult> results = response.results() != null
                ? response.results().stream()
                .map(result -> new WebSearchResult(result.title(), result.url(), result.content(), result.score(), result.rawContent()))
                .toList()
                : List.of();

        return new WebSearchResponse(response.answer(), results, response.responseTime());
    }

    private WebExtractResponse mapToWebExtractResponse(TavilyExtractResponse response) {
        if (response == null) {
            return new WebExtractResponse(List.of(), List.of("Extraction failed"));
        }

        List<WebExtractResult> results = response.results() != null
                ? response.results().stream()
                .map(result -> new WebExtractResult(result.url(), result.rawContent()))
                .toList()
                : List.of();

        List<String> errors = response.failedResults() != null
                ? response.failedResults().stream()
                .map(failed -> failed.url() + ": " + failed.error())
                .toList()
                : List.of();

        return new WebExtractResponse(results, errors);
    }

    public record WebSearchResponse(
            String answer,
            List<WebSearchResult> results,
            String responseTime
    ) {}

    public record WebSearchResult(
            String title,
            String url,
            String content,
            Double relevanceScore,
            String rawContent
    ) {}

    public record WebExtractResponse(
            List<WebExtractResult> extractedContent,
            List<String> errors
    ) {}

    public record WebExtractResult(
            String url,
            String content
    ) {}
}
