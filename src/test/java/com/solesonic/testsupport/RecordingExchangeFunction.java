package com.solesonic.testsupport;

import com.solesonic.mcp.security.identity.CallerIdentity;
import jakarta.annotation.Nonnull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A fake HTTP backend for service tests: answers each request with the next queued response (or
 * {@code 200 {}} when the queue is empty) and records every request so tests can assert on what
 * actually went out — including the {@link CallerIdentity} attribute.
 */
public class RecordingExchangeFunction implements ExchangeFunction {

    private final ConcurrentLinkedQueue<ClientResponse> queuedResponses = new ConcurrentLinkedQueue<>();
    private final List<ClientRequest> recordedRequests = new CopyOnWriteArrayList<>();

    @SuppressWarnings("all")
    public RecordingExchangeFunction respondWithJson(String json) {
        queuedResponses.add(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build());
        return this;
    }

    @SuppressWarnings("all")
    public RecordingExchangeFunction respondWithStatus(HttpStatus status) {
        queuedResponses.add(ClientResponse.create(status).build());
        return this;
    }

    public WebClient webClient() {
        return WebClient.builder().exchangeFunction(this).build();
    }

    public List<ClientRequest> requests() {
        return List.copyOf(recordedRequests);
    }

    public ClientRequest onlyRequest() {
        if (recordedRequests.size() != 1) {
            throw new AssertionError("Expected exactly one request but saw " + recordedRequests.size());
        }

        return recordedRequests.getFirst();
    }

    public static Optional<CallerIdentity> callerIdentityOf(ClientRequest request) {
        return CallerIdentity.from(request);
    }

    @Override
    @Nonnull
    public Mono<ClientResponse> exchange(@Nonnull ClientRequest request) {
        recordedRequests.add(request);

        ClientResponse queuedResponse = queuedResponses.poll();

        return Mono.just(Objects.requireNonNullElseGet(queuedResponse, () -> ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{}")
                .build()));

    }
}
