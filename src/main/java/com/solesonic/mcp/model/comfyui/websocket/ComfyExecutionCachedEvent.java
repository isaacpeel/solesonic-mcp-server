package com.solesonic.mcp.model.comfyui.websocket;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Represents an execution_cached event from ComfyUI WebSocket.
 * Indicates that certain nodes were cached and did not need re-execution.
 */
public record ComfyExecutionCachedEvent(
        String promptId,
        List<String> cachedNodes,
        long timestamp,
        JsonNode rawPayload
) implements ComfyWebSocketEvent {

    public static final String EVENT_TYPE = "execution_cached";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
