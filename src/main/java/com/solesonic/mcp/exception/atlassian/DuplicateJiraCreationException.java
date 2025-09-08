package com.solesonic.mcp.exception.atlassian;


public class DuplicateJiraCreationException extends RuntimeException {
    public DuplicateJiraCreationException(String message) {
        super(message);
    }
}
