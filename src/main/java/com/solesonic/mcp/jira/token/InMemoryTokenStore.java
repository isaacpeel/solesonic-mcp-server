package com.solesonic.mcp.jira.token;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory TokenStore implementation.
 * Note: Not suitable for production; future phases will add encrypted persistence.
 */
public class InMemoryTokenStore implements TokenStore {

    private final Map<String, StoredToken> store = new ConcurrentHashMap<>();

    private static String key(String userProfileId, String cloudId) {
        return userProfileId + ":" + cloudId;
    }

    @Override
    public Optional<StoredToken> get(String userProfileId, String cloudId) {
        return Optional.ofNullable(store.get(key(userProfileId, cloudId)));
    }

    @Override
    public void save(String userProfileId, String cloudId, StoredToken token) {
        store.put(key(userProfileId, cloudId), token);
    }

    @Override
    public void delete(String userProfileId, String cloudId) {
        store.remove(key(userProfileId, cloudId));
    }
}
