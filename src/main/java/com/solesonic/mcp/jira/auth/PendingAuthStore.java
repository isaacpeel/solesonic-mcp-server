package com.solesonic.mcp.jira.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for OAuth pending state and PKCE code_verifier per user profile.
 */
public class PendingAuthStore {
    public record Pending(String state, String codeVerifier, Instant expiresAt) {}

    private final Map<String, Pending> map = new ConcurrentHashMap<>();
    private final Duration ttl;

    public PendingAuthStore(Duration ttl) {
        this.ttl = ttl;
    }

    public Pending create(String userProfileId, String codeVerifier) {
        String state = UUID.randomUUID().toString();
        Pending p = new Pending(state, codeVerifier, Instant.now().plus(ttl));
        map.put(userProfileId, p);
        return p;
    }

    public Optional<Pending> get(String userProfileId) {
        Pending p = map.get(userProfileId);

        if (p == null) {
            return Optional.empty();
        }

        if (Instant.now().isAfter(p.expiresAt())) {
            map.remove(userProfileId);
            return Optional.empty();
        }

        return Optional.of(p);
    }

    /**
     * Consumes and removes pending state if valid, returning the code_verifier.
     */
    public Optional<String> consumeAndGetVerifier(String userProfileId, String state) {
        Pending p = map.get(userProfileId);

        if (p == null) {
            return Optional.empty();
        }

        if (!p.state().equals(state)) {
            return Optional.empty();
        }

        if (Instant.now().isAfter(p.expiresAt())) {
            map.remove(userProfileId);
            return Optional.empty();
        }

        map.remove(userProfileId);
        return Optional.ofNullable(p.codeVerifier());
    }
}
