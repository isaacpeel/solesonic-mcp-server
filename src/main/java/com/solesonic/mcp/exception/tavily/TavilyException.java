package com.solesonic.mcp.exception.tavily;

public class TavilyException extends RuntimeException {

    private final String rawResponse;

    public TavilyException(String message) {
        super(message);
        this.rawResponse = null;
    }

    public TavilyException(String message, String rawResponse) {
        super(message);
        this.rawResponse = rawResponse;
    }

    public TavilyException(String message, Throwable cause) {
        super(message, cause);
        this.rawResponse = null;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
