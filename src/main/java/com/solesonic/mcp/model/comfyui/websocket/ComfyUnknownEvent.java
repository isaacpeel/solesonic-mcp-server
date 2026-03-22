package com.solesonic.mcp.model.comfyui.websocket;

import tools.jackson.databind.JsonNode;

/**
 * Represents an unknown or unrecognized event from ComfyUI WebSocket.
 * Used as a fallback for forward compatibility when new event types are added.
 */
public record ComfyUnknownEvent(
        String eventType,
        String promptId,
        JsonNode rawPayload
) implements ComfyWebSocketEvent {
}
