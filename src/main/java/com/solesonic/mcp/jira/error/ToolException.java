package com.solesonic.mcp.jira.error;

/**
 * Structured exception carrying a ToolErrorCode and optional user-friendly message.
 */
public class ToolException extends RuntimeException {
    private final ToolErrorCode code;
    private final int httpStatus;

    public ToolException(ToolErrorCode code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = 0;
    }

    @SuppressWarnings("unused")
    public ToolException(ToolErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = 0;
    }

    public ToolException(ToolErrorCode code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public ToolErrorCode getCode() {
        return code;
    }

    @SuppressWarnings("unused")
    public int getHttpStatus() {
        return httpStatus;
    }
}
