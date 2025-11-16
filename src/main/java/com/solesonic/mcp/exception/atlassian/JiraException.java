package com.solesonic.mcp.exception.atlassian;

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

    public JiraException(String message, String responseBody) {
        super(message);
        this.responseBody = responseBody;
    }

    @SuppressWarnings("unused")
    public JiraException(String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.responseBody = responseBody;
    }

    @SuppressWarnings("unused")
    public String getResponseBody() {
        return responseBody;
    }
}
