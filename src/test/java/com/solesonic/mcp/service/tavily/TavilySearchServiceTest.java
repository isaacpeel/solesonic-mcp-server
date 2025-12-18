package com.solesonic.mcp.service.tavily;

import com.solesonic.mcp.model.tavily.TavilySearchRequest;
import com.solesonic.mcp.model.tavily.TavilySearchResponse;
import com.solesonic.mcp.model.tavily.TavilySearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TavilySearchServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private TavilySearchService tavilySearchService;

    @BeforeEach
    void setUp() {
        tavilySearchService = new TavilySearchService(webClient);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchReturnsResponse() {
        TavilySearchResult searchResult = new TavilySearchResult(
                "Test Title",
                "https://example.com",
                "Test content snippet",
                0.95,
                null,
                "https://example.com/favicon.ico"
        );

        TavilySearchResponse expectedResponse = new TavilySearchResponse(
                "test query",
                "This is the answer",
                List.of(searchResult),
                "0.5s",
                "req-123",
                List.of()
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(TavilySearchResponse.class))).thenReturn(Mono.just(expectedResponse));

        TavilySearchRequest request = TavilySearchRequest.builder()
                .query("test query")
                .maxResults(5)
                .build();

        TavilySearchResponse response = tavilySearchService.search(request);

        assertNotNull(response);
        assertEquals("test query", response.query());
        assertEquals("This is the answer", response.answer());
        assertNotNull(response.results());
        assertEquals(1, response.results().size());
        assertEquals("Test Title", response.results().getFirst().title());
    }
}
