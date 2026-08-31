package com.solesonic.mcp.service.google;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.mcp.exception.google.GmailLabelNotFoundException;
import com.solesonic.mcp.exception.google.GmailMessageNotFoundException;
import com.solesonic.model.google.gmail.GmailMessageBody;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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

    /** No body at all, as opposed to an empty one — the empty string still reaches the JSON decoder. */
    private void queueWithoutBody(@SuppressWarnings("all") HttpStatusCode statusCode) {
        queuedResponses.add(ClientResponse.create(statusCode).build());
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

        assertTrue(exception.getMessage().contains("Failed to list messages for label"));
        assertTrue(exception.getResponseBody().contains("backend error"));
    }

    @Test
    void listMessagesByLabel_resolvesTheLabelNameToItsId_andUsesItInTheListCall() {
        queue(OK, """
                {"labels":[{"id":"INBOX","name":"INBOX","type":"system"},
                            {"id":"Label_15","name":"Receipts","type":"user"}]}
                """);
        queue(OK, "{\"messages\":[{\"id\":\"m1\",\"threadId\":\"t1\"}]}");
        queue(OK, metadataJson("m1", "{\"name\":\"Subject\",\"value\":\"Your receipt\"}"));

        List<GmailMessageSummary> summaries = service().listMessagesByLabel("Receipts", 5);

        assertEquals(1, summaries.size());
        assertEquals("Your receipt", summaries.getFirst().subject());

        String labelsUri = requestedUris.get(0).toString();
        assertTrue(labelsUri.contains("/gmail/v1/users/me/labels"), labelsUri);

        String listUri = requestedUris.get(1).toString();
        assertTrue(listUri.contains("labelIds=Label_15"), listUri);
        assertTrue(listUri.contains("maxResults=5"), listUri);
    }

    @Test
    void listMessagesByLabel_matchesTheLabelNameCaseInsensitively() {
        queue(OK, "{\"labels\":[{\"id\":\"STARRED\",\"name\":\"STARRED\",\"type\":\"system\"}]}");
        queue(OK, "{\"messages\":[{\"id\":\"m1\",\"threadId\":\"t1\"}]}");
        queue(OK, metadataJson("m1", "{\"name\":\"Subject\",\"value\":\"Flagged\"}"));

        List<GmailMessageSummary> summaries = service().listMessagesByLabel("starred", 5);

        assertEquals(1, summaries.size());

        String listUri = requestedUris.get(1).toString();
        assertTrue(listUri.contains("labelIds=STARRED"), listUri);
    }

    @Test
    void listMessagesByLabel_throwsGmailLabelNotFoundException_whenNoLabelMatches() {
        queue(OK, "{\"labels\":[{\"id\":\"INBOX\",\"name\":\"INBOX\",\"type\":\"system\"}]}");

        GmailLabelNotFoundException exception = assertThrows(GmailLabelNotFoundException.class,
                () -> service().listMessagesByLabel("Does Not Exist", 5));

        assertTrue(exception.getMessage().contains("Does Not Exist"));
        assertEquals(1, requestedUris.size());
    }

    @Test
    void getMessageSummary_returnsTheSummaryForASingleId() {
        queue(OK, metadataJson("m1", """
                {"name":"Subject","value":"Invoice 42"},
                {"name":"From","value":"billing@example.com"},
                {"name":"Date","value":"Sun, 17 Aug 2026 12:00:00 -0500"}
                """));

        GmailMessageSummary summary = service().getMessageSummary("m1");

        assertEquals("m1", summary.id());
        assertEquals("Invoice 42", summary.subject());
        assertEquals("billing@example.com", summary.from());

        String requestedUri = requestedUris.getFirst().toString();
        assertTrue(requestedUri.contains("/gmail/v1/users/me/messages/m1"), requestedUri);
    }

    @Test
    void getMessageSummary_throwsGmailException_whenTheMessageIsNotFound() {
        queue(NOT_FOUND, "{\"error\":{\"message\":\"Not Found\"}}");

        GmailException exception = assertThrows(GmailException.class, () -> service().getMessageSummary("missing"));

        assertTrue(exception.getMessage().contains("missing"));
    }

    // --- get_gmail_message_body -------------------------------------------------------------

    private static final String STANDARD_HEADERS = """
            {"name":"Subject","value":"Budget review"},
            {"name":"From","value":"ada@example.com"},
            {"name":"Date","value":"Mon, 18 Aug 2026 09:00:00 -0500"}
            """;

    private String fullMessageJson(@SuppressWarnings("all") String id, String payloadJson) {
        return """
                {"id":"%s","threadId":"t-%s","snippet":"…","payload":%s}
                """.formatted(id, id, payloadJson);
    }

    private String plainTextPayload(String body) {
        return """
                {"mimeType":"text/plain","headers":[%s],"body":{"data":"%s","size":%d}}
                """.formatted(STANDARD_HEADERS, encoded(body), body.length());
    }

    private String encoded(String text) {
        return Base64.getUrlEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getMessageBody_asksGmailForTheFullFormat() {
        queue(OK, fullMessageJson("m1", plainTextPayload("Hello there")));

        service().getMessageBody("m1");

        String requestedUri = requestedUris.getFirst().toString();
        assertTrue(requestedUri.contains("/gmail/v1/users/me/messages/m1"), requestedUri);
        assertTrue(requestedUri.contains("format=full"), requestedUri);
        assertFalse(requestedUri.contains("metadataHeaders"), requestedUri);
    }

    @Test
    void getMessageBody_returnsTheBody_whenThePayloadIsItselfTextPlain() {
        queue(OK, fullMessageJson("m1", plainTextPayload("The quarterly numbers are attached.")));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertEquals("m1", messageBody.id());
        assertEquals("text/plain", messageBody.mimeType());
        assertEquals("The quarterly numbers are attached.", messageBody.body());
    }

    @Test
    void getMessageBody_prefersTextPlain_evenWhenTheHtmlPartComesFirst() {
        String payload = """
                {"mimeType":"multipart/alternative","headers":[%s],"parts":[
                  {"mimeType":"text/html","body":{"data":"%s"}},
                  {"mimeType":"text/plain","body":{"data":"%s"}}
                ]}
                """.formatted(STANDARD_HEADERS, encoded("<p>Markup</p>"), encoded("Plain words"));

        queue(OK, fullMessageJson("m1", payload));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertEquals("text/plain", messageBody.mimeType());
        assertEquals("Plain words", messageBody.body());
    }

    @Test
    void getMessageBody_fallsBackToTextHtml_andReturnsTheMarkupVerbatim() {
        String markup = "<html><body><p>Quarterly&nbsp;update</p></body></html>";

        String payload = """
                {"mimeType":"multipart/alternative","headers":[%s],"parts":[
                  {"mimeType":"text/html","body":{"data":"%s"}}
                ]}
                """.formatted(STANDARD_HEADERS, encoded(markup));

        queue(OK, fullMessageJson("m1", payload));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertEquals("text/html", messageBody.mimeType());
        assertEquals(markup, messageBody.body());
    }

    @Test
    void getMessageBody_findsTheTextPartInANestedMultipartTree() {
        String payload = """
                {"mimeType":"multipart/mixed","headers":[%s],"parts":[
                  {"mimeType":"multipart/alternative","parts":[
                    {"mimeType":"text/html","body":{"data":"%s"}},
                    {"mimeType":"text/plain","body":{"data":"%s"}}
                  ]},
                  {"mimeType":"application/pdf","body":{"attachmentId":"a1","size":8192}}
                ]}
                """.formatted(STANDARD_HEADERS, encoded("<p>ignored</p>"), encoded("Signed and returned."));

        queue(OK, fullMessageJson("m1", payload));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertEquals("text/plain", messageBody.mimeType());
        assertEquals("Signed and returned.", messageBody.body());
    }

    /**
     * Gmail encodes {@code body.data} with the URL-safe alphabet. This fixture's standard-alphabet
     * spelling would use '+' and '/', so {@code Base64.getDecoder()} rejects it outright — the
     * single most common bug when implementing this endpoint.
     */
    @Test
    void getMessageBody_decodesUrlSafeBase64_containingDashAndUnderscore() {
        String urlSafeData = "QnVkZ2V0IGFwcHJvdmVkIMO_w78g8J-OiQ==";

        String payload = """
                {"mimeType":"text/plain","headers":[%s],"body":{"data":"%s"}}
                """.formatted(STANDARD_HEADERS, urlSafeData);

        queue(OK, fullMessageJson("m1", payload));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertEquals("Budget approved ÿÿ 🎉", messageBody.body());
    }

    @Test
    void getMessageBody_returnsANullBody_whenNoPartCarriesText() {
        String payload = """
                {"mimeType":"multipart/mixed","headers":[%s],"parts":[
                  {"mimeType":"image/jpeg","body":{"attachmentId":"a1","size":2048}}
                ]}
                """.formatted(STANDARD_HEADERS);

        queue(OK, fullMessageJson("m1", payload));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertNull(messageBody.body());
        assertNull(messageBody.mimeType());
        assertEquals("Budget review", messageBody.subject());
    }

    @Test
    void getMessageBody_returnsANullBody_whenTheBase64DataIsCorrupt() {
        String payload = """
                {"mimeType":"text/plain","headers":[%s],"body":{"data":"!!! not base64 !!!"}}
                """.formatted(STANDARD_HEADERS);

        queue(OK, fullMessageJson("m1", payload));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertNull(messageBody.body());
        assertNull(messageBody.mimeType());
    }

    @Test
    void getMessageBody_readsSubjectFromAndDate_fromThePayloadHeaders() {
        queue(OK, fullMessageJson("m1", plainTextPayload("Body")));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertEquals("Budget review", messageBody.subject());
        assertEquals("ada@example.com", messageBody.from());
        assertEquals("Mon, 18 Aug 2026 09:00:00 -0500", messageBody.date());
    }

    @Test
    void getMessageBody_fallsBackWhenTheSubjectAndSenderHeadersAreMissing() {
        String payload = """
                {"mimeType":"text/plain",
                 "headers":[{"name":"Date","value":"Mon, 18 Aug 2026 09:00:00 -0500"}],
                 "body":{"data":"%s"}}
                """.formatted(encoded("No headers here"));

        queue(OK, fullMessageJson("m1", payload));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertEquals("(no subject)", messageBody.subject());
        assertEquals("(unknown sender)", messageBody.from());
    }

    @Test
    void getMessageBody_throwsGmailMessageNotFoundException_whenGmailReturns404() {
        queue(NOT_FOUND, "{\"error\":{\"message\":\"Not Found\"}}");

        GmailMessageNotFoundException exception = assertThrows(GmailMessageNotFoundException.class,
                () -> service().getMessageBody("missing"));

        assertTrue(exception.getMessage().contains("missing"), exception.getMessage());
    }

    @Test
    void getMessageBody_throwsGmailException_whenGmailFailsForAnyOtherReason() {
        queue(INTERNAL_SERVER_ERROR, "{\"error\":{\"message\":\"backend error\"}}");

        GmailException exception = assertThrows(GmailException.class, () -> service().getMessageBody("m1"));

        assertTrue(exception.getResponseBody().contains("backend error"), exception.getResponseBody());
    }

    /** A success status with nothing to deserialize leaves {@code block()} handing back null. */
    @Test
    void getMessageBody_throwsGmailException_whenGmailReturnsNoContent() {
        queueWithoutBody(NO_CONTENT);

        GmailException exception = assertThrows(GmailException.class, () -> service().getMessageBody("m1"));

        assertTrue(exception.getMessage().contains("m1"), exception.getMessage());
    }

    /**
     * The preferred part is chosen *and* decoded together: a corrupt text/plain part must not hide a
     * perfectly good text/html sibling behind a "no readable text body" answer.
     */
    @Test
    void getMessageBody_fallsBackToTheHtmlPart_whenThePlainTextPartFailsToDecode() {
        String payload = """
                {"mimeType":"multipart/alternative","headers":[%s],"parts":[
                  {"mimeType":"text/plain","body":{"data":"!!! not base64 !!!"}},
                  {"mimeType":"text/html","body":{"data":"%s"}}
                ]}
                """.formatted(STANDARD_HEADERS, encoded("<p>Still readable</p>"));

        queue(OK, fullMessageJson("m1", payload));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertEquals("text/html", messageBody.mimeType());
        assertEquals("<p>Still readable</p>", messageBody.body());
    }

    @Test
    void getMessageBody_skipsAnEmptyTextPart_inFavourOfOneThatCarriesContent() {
        String payload = """
                {"mimeType":"multipart/alternative","headers":[%s],"parts":[
                  {"mimeType":"text/plain","body":{"data":"","size":0}},
                  {"mimeType":"text/html","body":{"data":"%s"}}
                ]}
                """.formatted(STANDARD_HEADERS, encoded("<p>The real content</p>"));

        queue(OK, fullMessageJson("m1", payload));

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertEquals("text/html", messageBody.mimeType());
        assertEquals("<p>The real content</p>", messageBody.body());
    }

    @Test
    void getMessageBody_returnsANullBody_whenGmailOmitsThePayloadEntirely() {
        queue(OK, """
                {"id":"m1","threadId":"t-m1","snippet":"…"}
                """);

        GmailMessageBody messageBody = service().getMessageBody("m1");

        assertEquals("m1", messageBody.id());
        assertNull(messageBody.body());
        assertNull(messageBody.mimeType());
        assertEquals("(no subject)", messageBody.subject());
    }
}
