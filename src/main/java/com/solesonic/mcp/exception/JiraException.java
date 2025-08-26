package com.solesonic.mcp.exception;

@SuppressWarnings("unused")
public class JiraException extends RuntimeException {
    private String responseBody;

    public JiraException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public JiraException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public String getResponseBody() {
        return responseBody;
    }
}
