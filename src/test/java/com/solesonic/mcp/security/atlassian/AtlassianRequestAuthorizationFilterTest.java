package com.solesonic.mcp.security.atlassian;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.auth.TokenResponse;
import com.solesonic.service.atlassian.AtlassianTokenBrokerService;
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

class AtlassianRequestAuthorizationFilterTest {

    private static final UUID USER_ID = UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11");
    private static final URI BOARDS_URI = URI.create("https://api.atlassian.test/ex/jira/cloud/rest/agile/1.0/board");

    private AtlassianTokenBrokerService atlassianTokenBrokerService;
    private AtlassianRequestAuthorizationFilter filter;
    private AtomicReference<ClientRequest> exchangedRequest;

    @BeforeEach
    void setUp() {
        atlassianTokenBrokerService = mock(AtlassianTokenBrokerService.class);
        filter = new AtlassianRequestAuthorizationFilter(atlassianTokenBrokerService);
        exchangedRequest = new AtomicReference<>();
    }

    @Test
    void attachesBrokeredTokenForTheRequestsCallerIdentity() {
        when(atlassianTokenBrokerService.atlassianAccessToken(USER_ID)).thenReturn(tokenResponse("atlassian-token"));

        filter.filter(requestFor(new CallerIdentity(USER_ID)), this::recordExchange).block();

        assertEquals("Bearer atlassian-token", exchangedRequest.get().headers().getFirst(AUTHORIZATION));
    }

    @Test
    void attachesTokenEvenOnAPooledThreadWithNoSecurityContext() {
        when(atlassianTokenBrokerService.atlassianAccessToken(USER_ID)).thenReturn(tokenResponse("atlassian-token"));
        ClientRequest request = requestFor(new CallerIdentity(USER_ID));

        CompletableFuture.supplyAsync(() -> {
            SecurityContextHolder.clearContext();
            return filter.filter(request, this::recordExchange).block();
        }, ForkJoinPool.commonPool()).join();

        assertEquals("Bearer atlassian-token", exchangedRequest.get().headers().getFirst(AUTHORIZATION));
    }

    @Test
    @SuppressWarnings("all")
    void requestWithoutCallerIdentity_failsWithoutSendingAnything() {
        ClientRequest request = ClientRequest.create(HttpMethod.GET, BOARDS_URI).build();

        Mono<ClientResponse> response = filter.filter(request, this::recordExchange);

        assertThrows(JiraException.class, response::block);
        assertNull(exchangedRequest.get());
        verifyNoInteractions(atlassianTokenBrokerService);
    }

    @Test
    @SuppressWarnings("all")
    void blankBrokeredToken_failsWithoutSendingAnything() {
        when(atlassianTokenBrokerService.atlassianAccessToken(USER_ID)).thenReturn(tokenResponse(" "));

        Mono<ClientResponse> response = filter.filter(requestFor(new CallerIdentity(USER_ID)), this::recordExchange);

        assertThrows(JiraException.class, response::block);
        assertNull(exchangedRequest.get());
    }

    private Mono<ClientResponse> recordExchange(ClientRequest request) {
        exchangedRequest.set(request);
        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
    }

    private static ClientRequest requestFor(CallerIdentity callerIdentity) {
        return ClientRequest.create(HttpMethod.GET, BOARDS_URI)
                .attributes(callerIdentity.requestAttributes())
                .build();
    }

    private static TokenResponse tokenResponse(String accessToken) {
        return new TokenResponse(accessToken, 3600, ZonedDateTime.now(), USER_ID, "site");
    }
}
