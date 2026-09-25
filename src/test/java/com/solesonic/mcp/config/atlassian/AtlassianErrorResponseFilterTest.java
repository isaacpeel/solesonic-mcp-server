package com.solesonic.mcp.config.atlassian;

import com.solesonic.mcp.exception.atlassian.JiraException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlassianErrorResponseFilterTest {

    private static final URI BOARDS_URI = URI.create("https://api.atlassian.test/ex/jira/cloud/rest/agile/1.0/board");

    private final AtlassianErrorResponseFilter filter = new AtlassianErrorResponseFilter(JsonMapper.builder().build());

    @Test
    void nonJsonUnauthorizedBody_becomesJiraExceptionWithStatusAndBody() {
        ClientResponse unauthorized = ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body("Client must be authenticated to access this resource.")
                .build();

        Mono<ClientResponse> response = filter.filter(boardsRequest(), _ -> Mono.just(unauthorized));

        JiraException exception = assertThrows(JiraException.class, response::block);
        assertTrue(exception.getMessage().contains("401"));
        assertTrue(exception.getMessage().contains("/rest/agile/1.0/board"));
        assertTrue(exception.getMessage().contains("Client must be authenticated"));
        assertEquals("Client must be authenticated to access this resource.", exception.getResponseBody());
    }

    @Test
    void jiraJsonErrorBody_isSummarized() {
        String errorJson = "{\"errorMessages\":[\"Board does not exist\"],\"errors\":{\"jql\":\"bad clause\"}}";
        ClientResponse notFound = ClientResponse.create(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(errorJson)
                .build();

        Mono<ClientResponse> response = filter.filter(boardsRequest(), _ -> Mono.just(notFound));

        JiraException exception = assertThrows(JiraException.class, response::block);
        assertTrue(exception.getMessage().contains("Board does not exist; jql: bad clause"));
        assertEquals(errorJson, exception.getResponseBody());
    }

    @Test
    void successfulResponse_passesThroughUntouched() {
        ClientResponse ok = ClientResponse.create(HttpStatus.OK).body("{}").build();

        ClientResponse response = filter.filter(boardsRequest(), _ -> Mono.just(ok)).block();

        assertSame(ok, response);
    }

    @Test
    @SuppressWarnings("all")
    void appliesToExchangeToMonoCallers() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(_ -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body("Client must be authenticated to access this resource.")
                        .build()))
                .filter(filter)
                .build();

        Mono<String> body = webClient.get()
                .uri(BOARDS_URI)
                .exchangeToMono(clientResponse -> clientResponse.bodyToMono(String.class));

        assertThrows(JiraException.class, body::block);
    }

    private static ClientRequest boardsRequest() {
        return ClientRequest.create(HttpMethod.GET, BOARDS_URI).build();
    }
}
