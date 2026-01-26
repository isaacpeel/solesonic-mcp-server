package com.solesonic.mcp.service.comfyui;

/**
 * Exception thrown when a WebSocket connection to ComfyUI cannot be established or fails unexpectedly.
 */
public class ComfyWebSocketConnectionException extends RuntimeException {

    public ComfyWebSocketConnectionException(String message) {
        super(message);
    }

    public ComfyWebSocketConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
