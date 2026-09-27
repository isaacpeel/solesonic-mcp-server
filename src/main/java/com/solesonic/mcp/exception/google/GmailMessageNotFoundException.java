package com.solesonic.mcp.exception.google;

/**
 * No Gmail message matched the caller-supplied id, or it isn't visible to them. A distinct type so
 * tools can answer with an actionable sentence instead of matching on an error string.
 */
public class GmailMessageNotFoundException extends RuntimeException {

    public GmailMessageNotFoundException(String message) {
        super(message);
    }
}
