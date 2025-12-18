I'll analyze your project structure and understand how MCP tools are implemented to create a comprehensive plan for Tavily web search integration.Based on my thorough analysis of your `solesonic-mcp-server` project, I've prepared a comprehensive implementation plan for integrating Tavily web search MCP tools. This plan follows your existing architectural patterns and is geared toward your coding LLM for implementation.

---

# 🛠️ Tavily Web Search MCP Tools - Implementation Plan

## Overview

This plan integrates **Tavily Search API** (`https://api.tavily.com`) into the existing MCP server following the established patterns for Atlassian tools. All tools will be secured with a `WEB_SEARCH` role.

---

## Phase 1: Configuration & Dependencies

### 1.1 Update `pom.xml`
No additional dependencies required—existing `spring-boot-starter-webflux` and Jackson support HTTP client needs.

### 1.2 Create Configuration Properties

**File:** `src/main/resources/application.properties`
```properties
# Tavily Web Search Configuration
tavily.api.uri=https://api.tavily.com
tavily.api.key=${TAVILY_API_KEY}
```


**File:** `.env` (add)
```properties
TAVILY_API_KEY=tvly-YOUR_API_KEY_HERE
```


---

## Phase 2: Model Classes

Create model classes under `src/main/java/com/solesonic/mcp/model/tavily/`

### 2.1 Request Models

**File:** `TavilySearchRequest.java`
```java
package com.solesonic.mcp.model.tavily;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TavilySearchRequest(
    String query,
    @JsonProperty("search_depth") String searchDepth,      // "basic" or "advanced"
    String topic,                                           // "general" or "news"
    @JsonProperty("max_results") Integer maxResults,        // 1-20, default 5
    @JsonProperty("include_answer") Boolean includeAnswer,
    @JsonProperty("include_raw_content") Boolean includeRawContent,
    @JsonProperty("include_images") Boolean includeImages,
    @JsonProperty("include_domains") List<String> includeDomains,
    @JsonProperty("exclude_domains") List<String> excludeDomains,
    @JsonProperty("time_range") String timeRange            // "day", "week", "month", "year"
) {
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private String query;
        private String searchDepth = "basic";
        private String topic = "general";
        private Integer maxResults = 5;
        private Boolean includeAnswer = true;
        private Boolean includeRawContent = false;
        private Boolean includeImages = false;
        private List<String> includeDomains;
        private List<String> excludeDomains;
        private String timeRange;
        
        public Builder query(String query) { this.query = query; return this; }
        public Builder searchDepth(String searchDepth) { this.searchDepth = searchDepth; return this; }
        public Builder topic(String topic) { this.topic = topic; return this; }
        public Builder maxResults(Integer maxResults) { this.maxResults = maxResults; return this; }
        public Builder includeAnswer(Boolean includeAnswer) { this.includeAnswer = includeAnswer; return this; }
        public Builder includeRawContent(Boolean includeRawContent) { this.includeRawContent = includeRawContent; return this; }
        public Builder includeImages(Boolean includeImages) { this.includeImages = includeImages; return this; }
        public Builder includeDomains(List<String> includeDomains) { this.includeDomains = includeDomains; return this; }
        public Builder excludeDomains(List<String> excludeDomains) { this.excludeDomains = excludeDomains; return this; }
        public Builder timeRange(String timeRange) { this.timeRange = timeRange; return this; }
        
        public TavilySearchRequest build() {
            return new TavilySearchRequest(query, searchDepth, topic, maxResults, 
                includeAnswer, includeRawContent, includeImages, includeDomains, excludeDomains, timeRange);
        }
    }
}
```


**File:** `TavilyExtractRequest.java`
```java
package com.solesonic.mcp.model.tavily;

import java.util.List;

public record TavilyExtractRequest(
    List<String> urls
) {}
```


### 2.2 Response Models

**File:** `TavilySearchResponse.java`
```java
package com.solesonic.mcp.model.tavily;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TavilySearchResponse(
    String query,
    String answer,
    List<TavilySearchResult> results,
    @JsonProperty("response_time") String responseTime,
    @JsonProperty("request_id") String requestId,
    List<TavilyImage> images
) {}
```


**File:** `TavilySearchResult.java`
```java
package com.solesonic.mcp.model.tavily;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TavilySearchResult(
    String title,
    String url,
    String content,
    Double score,
    @JsonProperty("raw_content") String rawContent,
    String favicon
) {}
```


**File:** `TavilyImage.java`
```java
package com.solesonic.mcp.model.tavily;

public record TavilyImage(
    String url,
    String description
) {}
```


**File:** `TavilyExtractResponse.java`
```java
package com.solesonic.mcp.model.tavily;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TavilyExtractResponse(
    List<TavilyExtractResult> results,
    @JsonProperty("failed_results") List<TavilyFailedResult> failedResults
) {}
```


**File:** `TavilyExtractResult.java`
```java
package com.solesonic.mcp.model.tavily;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TavilyExtractResult(
    String url,
    @JsonProperty("raw_content") String rawContent
) {}
```


**File:** `TavilyFailedResult.java`
```java
package com.solesonic.mcp.model.tavily;

public record TavilyFailedResult(
    String url,
    String error
) {}
```


---

## Phase 3: Configuration Class

**File:** `src/main/java/com/solesonic/mcp/config/tavily/TavilyClientConfig.java`
```java
package com.solesonic.mcp.config.tavily;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Configuration
public class TavilyClientConfig {

    public static final String TAVILY_API_WEB_CLIENT = "tavilyApiWebClient";

    @Value("${tavily.api.uri}")
    private String tavilyApiUri;

    @Value("${tavily.api.key}")
    private String tavilyApiKey;

    @Bean
    @Qualifier(TAVILY_API_WEB_CLIENT)
    public WebClient tavilyApiWebClient(ObjectMapper objectMapper) {
        return WebClient.builder()
                .baseUrl(tavilyApiUri)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
                    httpHeaders.setBearerAuth(tavilyApiKey);
                })
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonEncoder(
                        new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                    configurer.defaultCodecs().jackson2JsonDecoder(
                        new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
                })
                .build();
    }
}
```


**File:** `src/main/java/com/solesonic/mcp/config/tavily/TavilyConstants.java`
```java
package com.solesonic.mcp.config.tavily;

public final class TavilyConstants {
    private TavilyConstants() {}
    
    public static final String SEARCH_ENDPOINT = "/search";
    public static final String EXTRACT_ENDPOINT = "/extract";
    public static final String USAGE_ENDPOINT = "/usage";
    
    // Search depths
    public static final String DEPTH_BASIC = "basic";
    public static final String DEPTH_ADVANCED = "advanced";
    
    // Topics
    public static final String TOPIC_GENERAL = "general";
    public static final String TOPIC_NEWS = "news";
    
    // Time ranges
    public static final String TIME_DAY = "day";
    public static final String TIME_WEEK = "week";
    public static final String TIME_MONTH = "month";
    public static final String TIME_YEAR = "year";
}
```


---

## Phase 4: Service Layer

**File:** `src/main/java/com/solesonic/mcp/service/tavily/TavilySearchService.java`
```java
package com.solesonic.mcp.service.tavily;

import com.solesonic.mcp.model.tavily.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static com.solesonic.mcp.config.tavily.TavilyClientConfig.TAVILY_API_WEB_CLIENT;
import static com.solesonic.mcp.config.tavily.TavilyConstants.*;

@Service
public class TavilySearchService {
    private static final Logger log = LoggerFactory.getLogger(TavilySearchService.class);

    private final WebClient webClient;

    public TavilySearchService(@Qualifier(TAVILY_API_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Performs a web search using Tavily API
     */
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

    /**
     * Extracts content from specified URLs
     */
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
```


---

## Phase 5: MCP Tool Classes

**File:** `src/main/java/com/solesonic/mcp/tool/tavily/WebSearchTools.java`
```java
package com.solesonic.mcp.tool.tavily;

import com.solesonic.mcp.model.tavily.*;
import com.solesonic.mcp.service.tavily.TavilySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.solesonic.mcp.config.tavily.TavilyConstants.*;

@SuppressWarnings("unused")
@Service
public class WebSearchTools {
    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);

    // Tool Names
    public static final String WEB_SEARCH = "web_search";
    public static final String WEB_SEARCH_ADVANCED = "web_search_advanced";
    public static final String WEB_SEARCH_NEWS = "web_search_news";
    public static final String WEB_EXTRACT_CONTENT = "web_extract_content";

    private final TavilySearchService tavilySearchService;

    public WebSearchTools(TavilySearchService tavilySearchService) {
        this.tavilySearchService = tavilySearchService;
    }

    // ========== TOOL DESCRIPTIONS ==========
    
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

    // ========== TOOL IMPLEMENTATIONS ==========

    /**
     * Basic web search - quick and efficient
     */
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

    /**
     * Advanced web search with filtering options
     */
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

    /**
     * News-specific search
     */
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

    /**
     * Extract full content from URLs
     */
    @PreAuthorize("hasAuthority('ROLE_MCP-WEB-SEARCH')")
    @McpTool(name = WEB_EXTRACT_CONTENT, description = WEB_EXTRACT_DESC)
    public WebExtractResponse webExtractContent(
            @McpToolParam(description = "List of URLs to extract content from. Maximum 5 URLs recommended.")
            List<String> urls
    ) {
        log.info("Executing content extraction for {} URLs", urls.size());

        if (urls == null || urls.isEmpty()) {
            return new WebExtractResponse(List.of(), List.of("No URLs provided"));
        }

        // Limit to 5 URLs for safety
        List<String> limitedUrls = urls.size() > 5 ? urls.subList(0, 5) : urls;

        TavilyExtractResponse response = tavilySearchService.extract(limitedUrls);
        
        return mapToWebExtractResponse(response);
    }

    // ========== RESPONSE MAPPING ==========

    private WebSearchResponse mapToWebSearchResponse(TavilySearchResponse response) {
        if (response == null) {
            return new WebSearchResponse(null, List.of(), null);
        }

        List<WebSearchResult> results = response.results() != null 
            ? response.results().stream()
                .map(r -> new WebSearchResult(r.title(), r.url(), r.content(), r.score(), r.rawContent()))
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
                .map(r -> new WebExtractResult(r.url(), r.rawContent()))
                .toList()
            : List.of();

        List<String> errors = response.failedResults() != null 
            ? response.failedResults().stream()
                .map(f -> f.url() + ": " + f.error())
                .toList()
            : List.of();

        return new WebExtractResponse(results, errors);
    }

    // ========== TOOL RESPONSE RECORDS ==========

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
```


---

## Phase 6: Security Configuration Updates

### 6.1 Add Role Constant

**File:** `src/main/java/com/solesonic/mcp/security/AuthoritiesService.java` (update)
```java
// ... existing code ...

// Add this constant for the WEB_SEARCH role
public static final String ROLE_MCP-WEB-SEARCH = "ROLE_MCP-WEB-SEARCH";

// ... existing code ...
```


### 6.2 JWT Configuration

Ensure your identity provider (IdP) includes the `WEB_SEARCH` role in the JWT `roles` claim for authorized users. The existing `AuthoritiesService.extractRoleAuthorities()` method will automatically map it to `ROLE_MCP-WEB-SEARCH`.

---

## Phase 7: Exception Handling

**File:** `src/main/java/com/solesonic/mcp/exception/tavily/TavilyException.java`
```java
package com.solesonic.mcp.exception.tavily;

public class TavilyException extends RuntimeException {
    private final String rawResponse;

    public TavilyException(String message) {
        super(message);
        this.rawResponse = null;
    }

    public TavilyException(String message, String rawResponse) {
        super(message);
        this.rawResponse = rawResponse;
    }

    public TavilyException(String message, Throwable cause) {
        super(message, cause);
        this.rawResponse = null;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
```


---

## Phase 8: Testing

**File:** `src/test/java/com/solesonic/mcp/service/tavily/TavilySearchServiceTest.java`
```java
package com.solesonic.mcp.service.tavily;

import com.solesonic.mcp.model.tavily.TavilySearchRequest;
import com.solesonic.mcp.model.tavily.TavilySearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TavilySearchServiceTest {

    @Autowired
    private TavilySearchService tavilySearchService;

    @Test
    void testBasicSearch() {
        TavilySearchRequest request = TavilySearchRequest.builder()
                .query("What is Spring Boot?")
                .maxResults(3)
                .build();

        TavilySearchResponse response = tavilySearchService.search(request);

        assertNotNull(response);
        assertNotNull(response.results());
        assertFalse(response.results().isEmpty());
    }
}
```


---

## File Structure Summary

```
src/main/java/com/solesonic/mcp/
├── config/
│   └── tavily/
│       ├── TavilyClientConfig.java          # WebClient configuration
│       └── TavilyConstants.java             # API constants
├── exception/
│   └── tavily/
│       └── TavilyException.java             # Custom exception
├── model/
│   └── tavily/
│       ├── TavilySearchRequest.java         # Search request DTO
│       ├── TavilySearchResponse.java        # Search response DTO
│       ├── TavilySearchResult.java          # Individual result
│       ├── TavilyImage.java                 # Image result
│       ├── TavilyExtractRequest.java        # Extract request DTO
│       ├── TavilyExtractResponse.java       # Extract response DTO
│       ├── TavilyExtractResult.java         # Extract result
│       └── TavilyFailedResult.java          # Failed extraction
├── service/
│   └── tavily/
│       └── TavilySearchService.java         # Business logic
└── tool/
    └── tavily/
        └── WebSearchTools.java              # MCP tool definitions
```


---

## Implementation Order

1. **Configuration** → Add properties and constants
2. **Models** → Create all DTOs
3. **Config Class** → WebClient setup with Bearer auth
4. **Service** → `TavilySearchService`
5. **Exception** → `TavilyException`
6. **Tools** → `WebSearchTools` with all 4 MCP tools
7. **Tests** → Unit and integration tests

---

## Role-Based Access Summary

| Tool | Required Authority |
|------|-------------------|
| `web_search` | `ROLE_MCP-WEB-SEARCH` |
| `web_search_advanced` | `ROLE_MCP-WEB-SEARCH` |
| `web_search_news` | `ROLE_MCP-WEB-SEARCH` |
| `web_extract_content` | `ROLE_MCP-WEB-SEARCH` |

All tools require the `WEB_SEARCH` role as specified. This integrates with your existing JWT-based security model.

---

## References

- Tavily API Documentation [[1]](https://docs.tavily.com/documentation/api-reference/introduction)
- Tavily Search Endpoint [[2]](https://docs.tavily.com/documentation/api-reference/endpoint/search)
- Tavily Best Practices [[3]](https://docs.tavily.com/documentation/best-practices/best-practices-search)

This plan follows your project's established patterns (Spring AI MCP annotations, `@PreAuthorize` security, record-based DTOs, WebClient for HTTP calls, and clean separation of concerns). Your coding LLM can implement each phase sequentially with all the necessary code provided! 🚀