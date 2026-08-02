package com.solesonic.model.security;

/**
 * Why a {@link SecurityEvent} fired. A closed enum rather than free text, for the same reason the
 * events are: fail2ban filters key off these strings, and prose drifts until the regexes quietly
 * stop matching.
 */
public enum SecurityEventReason {
    MISSING_TOKEN("missing_token"),
    MALFORMED_TOKEN("malformed_token"),
    EXPIRED_TOKEN("expired_token"),
    INVALID_SIGNATURE("invalid_signature"),
    WRONG_ISSUER("wrong_issuer"),
    WRONG_AUDIENCE("wrong_audience"),
    INSUFFICIENT_AUTHORITY("insufficient_authority");

    private final String key;

    SecurityEventReason(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
