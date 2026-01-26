package com.solesonic.mcp.service.comfyui;

import com.solesonic.mcp.model.comfyui.websocket.ComfyWebSocketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket client for connecting to ComfyUI and receiving execution events.
 * Manages multiple concurrent sessions identified by clientId.
 */
@Component
public class ComfyUiWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(ComfyUiWebSocketClient.class);

    private final String comfyUiBaseUrl;
    private final ComfyWebSocketEventParser eventParser;
    private final WebSocketClient webSocketClient;
    private final ConcurrentHashMap<UUID, ComfyWebSocketSession> sessions = new ConcurrentHashMap<>();

    public ComfyUiWebSocketClient(
            @Value("${comfyui.uri:https://comfy.izzy-bot.com}") String comfyUiBaseUrl,
            ComfyWebSocketEventParser eventParser) {
        this.comfyUiBaseUrl = comfyUiBaseUrl;
        this.eventParser = eventParser;
        this.webSocketClient = new ReactorNettyWebSocketClient();
    }

    /**
     * Opens a WebSocket session to ComfyUI for the given clientId.
     * If a session already exists for this clientId, returns the existing session's event stream.
     *
     * @param clientId the unique client identifier to associate with this session
     * @return a Flux of WebSocket events from ComfyUI
     */
    public Flux<ComfyWebSocketEvent> openSession(UUID clientId) {
        ComfyWebSocketSession existingSession = sessions.get(clientId);

        if (existingSession != null && !existingSession.isClosed()) {
            log.info("Returning existing WebSocket session for clientId: {}", clientId);
            return existingSession.events();
        }

        log.info("Opening new WebSocket session for clientId: {}", clientId);

        Sinks.Many<ComfyWebSocketEvent> eventSink = Sinks.many().multicast().onBackpressureBuffer();
        URI webSocketUri = buildWebSocketUri(clientId);

        Mono<Void> connectionMono = webSocketClient.execute(webSocketUri, webSocketSession -> {
            log.info("WebSocket connection established for clientId: {}", clientId);

            return webSocketSession.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(message -> {
                        log.info("Received WebSocket message for clientId {}: {}", clientId, message);
                        ComfyWebSocketEvent event = eventParser.parse(message);
                        eventSink.tryEmitNext(event);
                    })
                    .doOnError(error -> {
                        log.error("WebSocket error for clientId {}: {}", clientId, error.getMessage(), error);
                        eventSink.tryEmitError(new ComfyWebSocketConnectionException(
                                "WebSocket connection error for clientId: " + clientId, error));
                        sessions.remove(clientId);
                    })
                    .doOnComplete(() -> {
                        log.info("WebSocket connection closed for clientId: {}", clientId);
                        eventSink.tryEmitComplete();
                        sessions.remove(clientId);
                    })
                    .then();
        }).doOnError(error -> {
            log.error("Failed to establish WebSocket connection for clientId {}: {}", clientId, error.getMessage(), error);
            eventSink.tryEmitError(new ComfyWebSocketConnectionException(
                    "Failed to establish WebSocket connection for clientId: " + clientId, error));
            sessions.remove(clientId);
        });

        ComfyWebSocketSession session = new ComfyWebSocketSession(clientId, eventSink, connectionMono);
        sessions.put(clientId, session);

        connectionMono.subscribeOn(Schedulers.boundedElastic()).subscribe();

        return session.events();
    }

    /**
     * Closes the WebSocket session for the given clientId.
     *
     * @param clientId the client identifier of the session to close
     */
    public void closeSession(UUID clientId) {
        ComfyWebSocketSession session = sessions.remove(clientId);

        if (session != null) {
            log.info("Closing WebSocket session for clientId: {}", clientId);
            session.complete();
        } else {
            log.info("No active session found for clientId: {}", clientId);
        }
    }

    /**
     * Returns the session for the given clientId, if it exists and is not closed.
     *
     * @param clientId the client identifier
     * @return the session, or null if not found or closed
     */
    public ComfyWebSocketSession getSession(UUID clientId) {
        ComfyWebSocketSession session = sessions.get(clientId);

        if (session != null && session.isClosed()) {
            sessions.remove(clientId);
            return null;
        }

        return session;
    }

    /**
     * Returns true if there is an active session for the given clientId.
     *
     * @param clientId the client identifier
     * @return true if an active session exists
     */
    public boolean hasActiveSession(UUID clientId) {
        return getSession(clientId) != null;
    }

    private URI buildWebSocketUri(UUID clientId) {
        String wsUrl = comfyUiBaseUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://");

        String fullUrl = wsUrl + "/ws?clientId=" + clientId.toString();
        log.info("Built WebSocket URI: {}", fullUrl);

        return URI.create(fullUrl);
    }
}
