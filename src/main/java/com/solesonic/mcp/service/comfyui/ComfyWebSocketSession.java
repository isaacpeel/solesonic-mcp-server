package com.solesonic.mcp.service.comfyui;

import com.solesonic.mcp.model.comfyui.websocket.ComfyWebSocketEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.UUID;

/**
 * Represents an active WebSocket session to ComfyUI.
 * Provides access to the event stream and session lifecycle management.
 */
public class ComfyWebSocketSession {

    private final UUID clientId;
    private final Sinks.Many<ComfyWebSocketEvent> eventSink;
    private final Flux<ComfyWebSocketEvent> eventFlux;
    private final Mono<Void> connectionMono;
    private volatile boolean closed = false;

    public ComfyWebSocketSession(UUID clientId, Sinks.Many<ComfyWebSocketEvent> eventSink, Mono<Void> connectionMono) {
        this.clientId = clientId;
        this.eventSink = eventSink;
        this.eventFlux = eventSink.asFlux();
        this.connectionMono = connectionMono;
    }

    /**
     * Returns the client ID associated with this session.
     */
    public UUID getClientId() {
        return clientId;
    }

    /**
     * Returns a reactive stream of events from this WebSocket session.
     * The stream completes when the session is closed.
     */
    public Flux<ComfyWebSocketEvent> events() {
        return eventFlux;
    }

    /**
     * Returns the underlying connection Mono for lifecycle management.
     */
    public Mono<Void> getConnectionMono() {
        return connectionMono;
    }

    /**
     * Emits an event to all subscribers of this session.
     */
    public void emitEvent(ComfyWebSocketEvent event) {
        if (!closed) {
            eventSink.tryEmitNext(event);
        }
    }

    /**
     * Signals an error to all subscribers and marks the session as closed.
     */
    public void emitError(Throwable error) {
        if (!closed) {
            closed = true;
            eventSink.tryEmitError(error);
        }
    }

    /**
     * Completes the event stream and marks the session as closed.
     */
    public void complete() {
        if (!closed) {
            closed = true;
            eventSink.tryEmitComplete();
        }
    }

    /**
     * Returns true if this session has been closed.
     */
    public boolean isClosed() {
        return closed;
    }
}
