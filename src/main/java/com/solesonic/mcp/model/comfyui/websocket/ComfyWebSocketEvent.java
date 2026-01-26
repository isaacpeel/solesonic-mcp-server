package com.solesonic.mcp.model.comfyui.websocket;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Base interface for all ComfyUI WebSocket events.
 * Events are received from the ComfyUI server via WebSocket connection
 * and represent various stages of prompt execution.
 */
public sealed interface ComfyWebSocketEvent permits
        ComfyStatusEvent,
        ComfyExecutionStartEvent,
        ComfyExecutionCachedEvent,
        ComfyExecutingEvent,
        ComfyProgressEvent,
        ComfyExecutionSuccessEvent,
        ComfyExecutionErrorEvent,
        ComfyUnknownEvent {

    /**
     * Returns the event type identifier as received from ComfyUI.
     */
    String eventType();

    /**
     * Returns the prompt ID associated with this event, if available.
     */
    String promptId();

    /**
     * Returns the raw JSON payload for forward compatibility and debugging.
     */
    JsonNode rawPayload();
}
