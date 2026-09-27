package com.solesonic.mcp.security.google;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.google.auth.GoogleTokenResponse;
import com.solesonic.service.google.GoogleTokenBrokerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

class GoogleRequestAuthorizationFilterTest {

    private static final UUID USER_ID = UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11");
    private static final URI MESSAGES_URI = URI.create("https://gmail.googleapis.test/gmail/v1/users/me/messages");

    private GoogleTokenBrokerService googleTokenBrokerService;
    private GoogleRequestAuthorizationFilter filter;
    private AtomicReference<ClientRequest> exchangedRequest;

    @BeforeEach
    void setUp() {
        googleTokenBrokerService = mock(GoogleTokenBrokerService.class);
        filter = new GoogleRequestAuthorizationFilter(googleTokenBrokerService);
        exchangedRequest = new AtomicReference<>();
    }

    @Test
    void attachesBrokeredTokenForTheRequestsCallerIdentity() {
        when(googleTokenBrokerService.googleAccessToken(USER_ID)).thenReturn(tokenResponse("google-token"));

        filter.filter(requestFor(new CallerIdentity(USER_ID)), this::recordExchange).block();

        assertEquals("Bearer google-token", exchangedRequest.get().headers().getFirst(AUTHORIZATION));
    }

    @Test
    void attachesTokenEvenOnAPooledThreadWithNoSecurityContext() {
        when(googleTokenBrokerService.googleAccessToken(USER_ID)).thenReturn(tokenResponse("google-token"));
        ClientRequest request = requestFor(new CallerIdentity(USER_ID));

        CompletableFuture.supplyAsync(() -> {
            SecurityContextHolder.clearContext();
            return filter.filter(request, this::recordExchange).block();
        }, ForkJoinPool.commonPool()).join();

        assertEquals("Bearer google-token", exchangedRequest.get().headers().getFirst(AUTHORIZATION));
    }

    @Test
    @SuppressWarnings("all")
    void requestWithoutCallerIdentity_failsWithoutSendingAnything() {
        ClientRequest request = ClientRequest.create(HttpMethod.GET, MESSAGES_URI).build();

        Mono<ClientResponse> response = filter.filter(request, this::recordExchange);

        assertThrows(GmailException.class, response::block);
        assertNull(exchangedRequest.get());
        verifyNoInteractions(googleTokenBrokerService);
    }

    @Test
    @SuppressWarnings("all")
    void emptyBrokeredToken_failsWithoutSendingAnything() {
        when(googleTokenBrokerService.googleAccessToken(USER_ID)).thenReturn(tokenResponse(""));

        Mono<ClientResponse> response = filter.filter(requestFor(new CallerIdentity(USER_ID)), this::recordExchange);

        assertThrows(GmailException.class, response::block);
        assertNull(exchangedRequest.get());
    }

    private Mono<ClientResponse> recordExchange(ClientRequest request) {
        exchangedRequest.set(request);
        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
    }

    private static ClientRequest requestFor(CallerIdentity callerIdentity) {
        return ClientRequest.create(HttpMethod.GET, MESSAGES_URI)
                .attributes(callerIdentity.requestAttributes())
                .build();
    }

    private static GoogleTokenResponse tokenResponse(String accessToken) {
        return new GoogleTokenResponse(accessToken, 3600, ZonedDateTime.now(), USER_ID);
    }
}
