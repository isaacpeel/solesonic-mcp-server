package com.solesonic.mcp.service.google;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.mcp.exception.google.GoogleReconnectRequiredException;
import com.solesonic.model.google.auth.GoogleTokenResponse;
import com.solesonic.service.google.GoogleTokenBrokerService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

class GoogleTokenBrokerServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final Deque<ClientResponse> queuedResponses = new ArrayDeque<>();
    private final AtomicInteger brokerCalls = new AtomicInteger();

    private GoogleTokenBrokerService service() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://broker.example.com/broker/google/token")
                .exchangeFunction(_ -> {
                    brokerCalls.incrementAndGet();

                    return Mono.just(queuedResponses.removeFirst());
                })
                .build();

        return new GoogleTokenBrokerService(webClient, JsonMapper.builder().build());
    }

    private void queue(HttpStatusCode statusCode, String body) {
        queuedResponses.add(ClientResponse.create(statusCode)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private void queueToken(String accessToken, int expiresInSeconds, ZonedDateTime issuedAt) {
        queue(OK, """
                {"accessToken":"%s","expiresInSeconds":%d,"issuedAt":"%s","userId":"%s"}
                """.formatted(accessToken, expiresInSeconds, issuedAt, USER_ID));
    }

    @Test
    void googleAccessToken_returnsTheMintedToken() {
        queueToken("ya29.token", 3600, ZonedDateTime.now());

        GoogleTokenResponse tokenResponse = service().googleAccessToken(USER_ID);

        assertEquals("ya29.token", tokenResponse.accessToken());
        assertEquals(USER_ID, tokenResponse.userId());
        assertEquals(1, brokerCalls.get());
    }

    @Test
    void googleAccessToken_reusesACachedTokenWithinItsLifetime() {
        queueToken("ya29.cached", 3600, ZonedDateTime.now());

        GoogleTokenBrokerService service = service();

        GoogleTokenResponse first = service.googleAccessToken(USER_ID);
        GoogleTokenResponse second = service.googleAccessToken(USER_ID);

        assertSame(first, second);
        assertEquals(1, brokerCalls.get());
    }

    @Test
    void googleAccessToken_refetchesWhenTheCachedTokenIsWithinTheExpirySkew() {
        // 90 seconds of nominal life issued 60 seconds ago leaves 30 - inside the 60 second skew.
        queueToken("ya29.stale", 90, ZonedDateTime.now().minusSeconds(60));
        queueToken("ya29.fresh", 3600, ZonedDateTime.now());

        GoogleTokenBrokerService service = service();

        assertEquals("ya29.stale", service.googleAccessToken(USER_ID).accessToken());
        assertEquals("ya29.fresh", service.googleAccessToken(USER_ID).accessToken());
        assertEquals(2, brokerCalls.get());
    }

    @Test
    void googleAccessToken_throwsReconnectRequired_whenTheUserHasNoGrant() {
        queue(BAD_REQUEST, """
                {"code":"RECONNECT_REQUIRED","message":"Google access is no longer valid."}
                """);

        assertThrows(GoogleReconnectRequiredException.class, () -> service().googleAccessToken(USER_ID));
    }

    @Test
    void googleAccessToken_throwsGmailException_whenTheBrokerIsUnavailable() {
        queue(SERVICE_UNAVAILABLE, """
                {"code":"UPSTREAM_UNAVAILABLE","message":"Google is temporarily unavailable."}
                """);

        GmailException exception = assertThrows(GmailException.class, () -> service().googleAccessToken(USER_ID));

        assertTrue(exception.getMessage().contains("Google token broker failed"));
    }

    @Test
    void googleAccessToken_treatsANonJsonRejectionAsAFailure_notAReconnect() {
        queue(SERVICE_UNAVAILABLE, "<html><body>502 Bad Gateway</body></html>");

        assertThrows(GmailException.class, () -> service().googleAccessToken(USER_ID));
    }

    @Test
    void googleAccessToken_throwsGmailException_whenNoAccessTokenComesBack() {
        queue(OK, """
                {"accessToken":null,"expiresInSeconds":3600,"issuedAt":"%s","userId":"%s"}
                """.formatted(ZonedDateTime.now(), USER_ID));

        GmailException exception = assertThrows(GmailException.class, () -> service().googleAccessToken(USER_ID));

        assertTrue(exception.getMessage().contains("no access token"));
    }
}
