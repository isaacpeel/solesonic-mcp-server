package com.solesonic.service.comfyui;

import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ComfyEvent.Disconnected;
import com.solesonic.model.comfyui.ComfyEvent.QueueStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.QUERY_PARAM_CLIENT_ID;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.WEBSOCKET_ENDPOINT;

/**
 * Connects to ComfyUI's {@code /ws} endpoint, one socket per generation.
 *
 * <p>ComfyUI sends a prompt's events only to a socket that is already connected under the
 * prompt's {@code client_id}, and buffers nothing. {@link #open} therefore waits for the
 * {@code status} frame ComfyUI sends on connect, which confirms the socket is registered, before the
 * caller submits.
 */
@Service
public class ComfyUiWebSocketEventStream implements ComfyUiEventStream {

    /**
     * An {@code execution_error} frame carries the traceback and the failing node's inputs, which
     * can pass Netty's 64KB default frame limit.
     */
    private static final int MAX_FRAME_PAYLOAD_BYTES = 4 * 1024 * 1024;

    private final WebSocketClient webSocketClient;
    private final ComfyEventParser comfyEventParser;
    private final String comfyUiApiUri;
    private final long connectTimeoutMillis;

    public ComfyUiWebSocketEventStream(
            JsonMapper jsonMapper,
            @Value("${comfyui.api.uri}") String comfyUiApiUri,
            @Value("${comfyui.events.connect-timeout-millis}") long connectTimeoutMillis
    ) {
        this.webSocketClient = new ReactorNettyWebSocketClient(HttpClient.create(),
                () -> WebsocketClientSpec.builder().maxFramePayloadLength(MAX_FRAME_PAYLOAD_BYTES));
        this.comfyEventParser = new ComfyEventParser(jsonMapper);
        this.comfyUiApiUri = comfyUiApiUri;
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    @Override
    public ComfyEventSubscription open(String clientId) {
        URI webSocketUri = webSocketUri(clientId);
        Disposable.Swap connection = Disposables.swap();
        ComfyEventSubscription comfyEventSubscription = new ComfyEventSubscription(connection);
        CompletableFuture<Void> registered = new CompletableFuture<>();
        AtomicBoolean registrationSeen = new AtomicBoolean();

        Mono<Void> session = webSocketClient.execute(webSocketUri, webSocketSession -> webSocketSession.receive()
                .filter(webSocketMessage -> webSocketMessage.getType() == WebSocketMessage.Type.TEXT)
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(frame -> comfyEventParser.parse(frame).ifPresent(comfyEvent -> {
                    //The connect-time status describes the queue before this caller's prompt is in it,
                    //so it confirms registration but is not passed on as a queue position.
                    if (comfyEvent instanceof QueueStatus && registrationSeen.compareAndSet(false, true)) {
                        registered.complete(null);
                        return;
                    }

                    comfyEventSubscription.publish(comfyEvent);
                }))
                .then());

        //Closing the subscription cancels the session, which signals neither branch below.
        Mono<Void> lifecycle = session
                .then(Mono.<Void>fromRunnable(() -> {
                    registered.completeExceptionally(new ComfyUiException("ComfyUI closed the event stream"));
                    comfyEventSubscription.publish(new Disconnected("ComfyUI closed the event stream"));
                }))
                .doOnError(error -> {
                    registered.completeExceptionally(error);
                    comfyEventSubscription.publish(new Disconnected(error.getMessage()));
                })
                .onErrorComplete();

        connection.update(lifecycle.subscribe());

        try {
            registered.get(connectTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            comfyEventSubscription.close();
            Thread.currentThread().interrupt();
            throw new ComfyUiException("Interrupted while opening the ComfyUI event stream", interruptedException);
        } catch (ExecutionException | TimeoutException exception) {
            comfyEventSubscription.close();
            throw new ComfyUiException("Could not open the ComfyUI event stream at " + webSocketUri, exception);
        }

        return comfyEventSubscription;
    }

    private URI webSocketUri(String clientId) {
        URI apiUri = URI.create(comfyUiApiUri);
        String scheme = "https".equalsIgnoreCase(apiUri.getScheme()) ? "wss" : "ws";

        return UriComponentsBuilder.fromUri(apiUri)
                .scheme(scheme)
                .path(WEBSOCKET_ENDPOINT)
                .queryParam(QUERY_PARAM_CLIENT_ID, clientId)
                .build()
                .toUri();
    }
}
