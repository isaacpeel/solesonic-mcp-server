package com.solesonic.mcp.exception.google;

public class GmailException extends RuntimeException {
    private String responseBody;

    public GmailException(String message) {
        super(message);
    }

    public GmailException(String message, String responseBody) {
        super(message);
        this.responseBody = responseBody;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
