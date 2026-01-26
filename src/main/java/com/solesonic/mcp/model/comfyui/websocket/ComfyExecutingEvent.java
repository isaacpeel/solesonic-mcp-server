package com.solesonic.mcp.model.comfyui.websocket;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents an executing event from ComfyUI WebSocket.
 * Indicates which node is currently being executed.
 * When node is null, it indicates execution has completed for the prompt.
 */
public record ComfyExecutingEvent(
        String promptId,
        String node,
        JsonNode rawPayload
) implements ComfyWebSocketEvent {

    public static final String EVENT_TYPE = "executing";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /**
     * Returns true if this event indicates execution has completed (node is null).
     */
    public boolean isExecutionComplete() {
        return node == null;
    }
}
