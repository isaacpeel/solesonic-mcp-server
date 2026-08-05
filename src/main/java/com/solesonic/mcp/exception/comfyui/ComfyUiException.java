package com.solesonic.mcp.exception.comfyui;

public class ComfyUiException extends RuntimeException {

    private final String rawResponse;

    public ComfyUiException(String message) {
        super(message);
        this.rawResponse = null;
    }

    public ComfyUiException(String message, String rawResponse) {
        super(message);
        this.rawResponse = rawResponse;
    }

    public ComfyUiException(String message, Throwable cause) {
        super(message, cause);
        this.rawResponse = null;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
