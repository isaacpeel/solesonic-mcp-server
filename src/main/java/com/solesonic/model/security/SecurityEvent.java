package com.solesonic.model.security;

/**
 * The closed set of events written to the fail2ban-facing security log.
 * <p>
 * Every {@link #key()} is part of a machine interface: the jail filters under
 * {@code /etc/fail2ban/filter.d} match these exact strings. Renaming one does not break a build —
 * it silently stops a jail from matching, which looks exactly like "no attacks today". Treat the
 * keys as an API and change them only alongside the filters.
 */
public enum SecurityEvent {
    /**
     * Missing, malformed, or expired credential. Banned by route classification: a 401 for a path
     * this application does not serve is a scanner, a 401 for a path it does serve could be a real
     * client with a stale token.
     */
    AUTHENTICATION_FAILURE("authn.failure"),

    /**
     * Authenticated, but not permitted — the 403 path (a {@code @PreAuthorize} denial).
     */
    AUTHORIZATION_DENIED("authz.denied");

    private final String key;

    SecurityEvent(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
