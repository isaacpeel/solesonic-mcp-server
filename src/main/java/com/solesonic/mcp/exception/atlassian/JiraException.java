package com.solesonic.mcp.exception.atlassian;

public class JiraException extends RuntimeException {
    private String responseBody;

    public JiraException(String message) {
        super(message);
    }

    public JiraException(String message, String responseBody) {
        super(message);
        this.responseBody = responseBody;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
