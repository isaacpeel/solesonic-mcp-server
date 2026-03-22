package com.solesonic.mcp.model.comfyui.websocket;

import tools.jackson.databind.JsonNode;

/**
 * Represents an execution_start event from ComfyUI WebSocket.
 * Indicates that execution has started for a prompt.
 */
public record ComfyExecutionStartEvent(
        String promptId,
        long timestamp,
        JsonNode rawPayload
) implements ComfyWebSocketEvent {

    public static final String EVENT_TYPE = "execution_start";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
