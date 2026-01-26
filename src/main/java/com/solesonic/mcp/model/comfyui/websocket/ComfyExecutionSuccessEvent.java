package com.solesonic.mcp.model.comfyui.websocket;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents an execution_success event from ComfyUI WebSocket.
 * Indicates that prompt execution completed successfully.
 */
public record ComfyExecutionSuccessEvent(
        String promptId,
        long timestamp,
        JsonNode rawPayload
) implements ComfyWebSocketEvent {

    public static final String EVENT_TYPE = "execution_success";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
