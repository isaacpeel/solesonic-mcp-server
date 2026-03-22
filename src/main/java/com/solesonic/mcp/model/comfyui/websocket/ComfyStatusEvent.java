package com.solesonic.mcp.model.comfyui.websocket;

import tools.jackson.databind.JsonNode;

/**
 * Represents a status event from ComfyUI WebSocket.
 * Contains information about the current queue status.
 */
public record ComfyStatusEvent(
        String promptId,
        int queueRemaining,
        JsonNode rawPayload
) implements ComfyWebSocketEvent {

    public static final String EVENT_TYPE = "status";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
