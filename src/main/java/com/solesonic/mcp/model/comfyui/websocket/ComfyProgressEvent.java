package com.solesonic.mcp.model.comfyui.websocket;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a progress event from ComfyUI WebSocket.
 * Contains step progress information during node execution (e.g., sampling steps).
 */
public record ComfyProgressEvent(
        String promptId,
        String node,
        int currentStep,
        int totalSteps,
        JsonNode rawPayload
) implements ComfyWebSocketEvent {

    public static final String EVENT_TYPE = "progress";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /**
     * Returns the progress as a percentage (0-100).
     */
    public int progressPercent() {
        if (totalSteps <= 0) {
            return 0;
        }

        return (int) ((currentStep * 100.0) / totalSteps);
    }
}
