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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes"})
class TavilySearchServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private TavilySearchService tavilySearchService;

    @BeforeEach
    void setUp() {
        tavilySearchService = new TavilySearchService(webClient);
    }

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
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(any(String.class));
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
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
