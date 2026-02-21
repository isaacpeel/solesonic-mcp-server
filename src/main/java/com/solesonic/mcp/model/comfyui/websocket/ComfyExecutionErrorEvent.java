package com.solesonic.mcp.model.comfyui.websocket;

import tools.jackson.databind.JsonNode;

/**
 * Represents an execution_error event from ComfyUI WebSocket.
 * Indicates that prompt execution failed with an error.
 */
public record ComfyExecutionErrorEvent(
        String promptId,
        String errorMessage,
        String exceptionType,
        String exceptionMessage,
        JsonNode rawPayload
) implements ComfyWebSocketEvent {

    public static final String EVENT_TYPE = "execution_error";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
