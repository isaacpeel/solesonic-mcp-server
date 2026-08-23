package com.solesonic.mcp.exception.google;

/**
 * No Gmail label matched the caller-supplied name. A distinct type so tools can answer with an
 * actionable sentence instead of matching on an error string.
 */
public class GmailLabelNotFoundException extends RuntimeException {

    public GmailLabelNotFoundException(String message) {
        super(message);
    }
}
