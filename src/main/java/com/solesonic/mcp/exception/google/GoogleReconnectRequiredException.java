package com.solesonic.mcp.exception.google;

/**
 * The user has never connected their Google account, or the grant has been revoked. A distinct type
 * so tools can answer with an actionable sentence instead of matching on an error string.
 */
public class GoogleReconnectRequiredException extends RuntimeException {

    public GoogleReconnectRequiredException(String message) {
        super(message);
    }
}
