package com.solesonic.mcp.service.google;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.model.google.gmail.GmailMessageSummary;
import com.solesonic.service.google.GmailMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;

/**
 * Drives the real WebClient through a stub exchange function so URI building, deserialization and
 * header extraction are all genuinely exercised rather than stubbed away.
 */
class GmailMessageServiceTest {

    private final Deque<ClientResponse> queuedResponses = new ArrayDeque<>();
    private final List<URI> requestedUris = new ArrayList<>();

    private GmailMessageService service() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://gmail.googleapis.com")
                .exchangeFunction(clientRequest -> {
                    requestedUris.add(clientRequest.url());

                    return Mono.just(queuedResponses.removeFirst());
                })
                .build();

        return new GmailMessageService(webClient);
    }

    private void queue(HttpStatusCode statusCode, String body) {
        queuedResponses.add(ClientResponse.create(statusCode)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private String metadataJson(String id, String headersJson) {
        return """
                {"id":"%s","threadId":"t-%s","snippet":"…","payload":{"headers":[%s]}}
                """.formatted(id, id, headersJson);
    }

    @Test
    void listInboxMessages_returnsOneSummaryPerMessage_inOrder() {
        queue(OK, """
                {"messages":[{"id":"m1","threadId":"t1"},{"id":"m2","threadId":"t2"}],
                 "resultSizeEstimate":2}
                """);
        queue(OK, metadataJson("m1", """
                {"name":"Subject","value":"Standup notes"},
                {"name":"From","value":"Ada <ada@example.com>"},
                {"name":"Date","value":"Mon, 18 Aug 2026 09:00:00 -0500"}
                """));
        queue(OK, metadataJson("m2", """
                {"name":"Subject","value":"Invoice 42"},
                {"name":"From","value":"billing@example.com"},
                {"name":"Date","value":"Sun, 17 Aug 2026 12:00:00 -0500"}
                """));

        List<GmailMessageSummary> summaries = service().listInboxMessages(10);

        assertEquals(2, summaries.size());

        assertEquals("m1", summaries.getFirst().id());
        assertEquals("Standup notes", summaries.getFirst().subject());
        assertEquals("Ada <ada@example.com>", summaries.getFirst().from());
        assertEquals("Mon, 18 Aug 2026 09:00:00 -0500", summaries.getFirst().date());

        assertEquals("Invoice 42", summaries.getLast().subject());
    }

    @Test
    void listInboxMessages_buildsExpectedUris() {
        queue(OK, "{\"messages\":[{\"id\":\"m1\",\"threadId\":\"t1\"}]}");
        queue(OK, metadataJson("m1", "{\"name\":\"Subject\",\"value\":\"Hello\"}"));

        service().listInboxMessages(3);

        String listUri = requestedUris.getFirst().toString();
        assertTrue(listUri.contains("/gmail/v1/users/me/messages"), listUri);
        assertTrue(listUri.contains("labelIds=INBOX"), listUri);
        assertTrue(listUri.contains("maxResults=3"), listUri);

        String metadataUri = requestedUris.getLast().toString();
        assertTrue(metadataUri.contains("/gmail/v1/users/me/messages/m1"), metadataUri);
        assertTrue(metadataUri.contains("format=metadata"), metadataUri);
        assertTrue(metadataUri.contains("metadataHeaders=Subject"), metadataUri);
        assertTrue(metadataUri.contains("metadataHeaders=From"), metadataUri);
        assertTrue(metadataUri.contains("metadataHeaders=Date"), metadataUri);
    }

    @Test
    void listInboxMessages_matchesHeaderNamesCaseInsensitively() {
        queue(OK, "{\"messages\":[{\"id\":\"m1\",\"threadId\":\"t1\"}]}");
        queue(OK, metadataJson("m1", """
                {"name":"subject","value":"Lowercase header"},
                {"name":"FROM","value":"shouty@example.com"}
                """));

        GmailMessageSummary summary = service().listInboxMessages(1).getFirst();

        assertEquals("Lowercase header", summary.subject());
        assertEquals("shouty@example.com", summary.from());
    }

    @Test
    void listInboxMessages_fallsBackWhenHeadersAreMissing() {
        queue(OK, "{\"messages\":[{\"id\":\"m1\",\"threadId\":\"t1\"}]}");
        queue(OK, metadataJson("m1", "{\"name\":\"Date\",\"value\":\"Mon, 18 Aug 2026 09:00:00 -0500\"}"));

        GmailMessageSummary summary = service().listInboxMessages(1).getFirst();

        assertEquals("(no subject)", summary.subject());
        assertEquals("(unknown sender)", summary.from());
        assertEquals("Mon, 18 Aug 2026 09:00:00 -0500", summary.date());
    }

    @Test
    void listInboxMessages_returnsEmptyList_whenGmailOmitsTheMessagesField() {
        queue(OK, "{\"resultSizeEstimate\":0}");

        assertEquals(List.of(), service().listInboxMessages(10));
        assertEquals(1, requestedUris.size());
    }

    @Test
    void listInboxMessages_throwsGmailException_whenTheListCallFails() {
        queue(INTERNAL_SERVER_ERROR, "{\"error\":{\"message\":\"backend error\"}}");

        GmailException exception = assertThrows(GmailException.class, () -> service().listInboxMessages(10));

        assertTrue(exception.getMessage().contains("Failed to list inbox messages"));
        assertTrue(exception.getResponseBody().contains("backend error"));
    }
}
