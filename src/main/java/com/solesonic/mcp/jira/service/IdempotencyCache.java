package com.solesonic.mcp.jira.service;

import com.solesonic.mcp.jira.model.CreateJiraIssueResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-user/profile idempotency cache mapping request hash to the last successful response.
 */
public class IdempotencyCache {
    private static class Entry {
        final CreateJiraIssueResponse response;
        final Instant expiresAt;
        Entry(CreateJiraIssueResponse response, Instant expiresAt) {
            this.response = response;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final Duration ttl;

    public IdempotencyCache(Duration ttl) {
        this.ttl = ttl;
    }

    public Optional<CreateJiraIssueResponse> get(String userProfileId, String requestHash) {
        String key = userProfileId + ":" + requestHash;
        Entry e = map.get(key);
        if (e == null) return Optional.empty();
        if (Instant.now().isAfter(e.expiresAt)) {
            map.remove(key);
            return Optional.empty();
        }
        return Optional.of(e.response);
    }

    public void put(String userProfileId, String requestHash, CreateJiraIssueResponse response) {
        String key = userProfileId + ":" + requestHash;
        map.put(key, new Entry(response, Instant.now().plus(ttl)));
    }
}
