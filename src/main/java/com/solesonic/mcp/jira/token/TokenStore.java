package com.solesonic.mcp.jira.token;

import java.util.Optional;

/**
 * Token store abstraction with per-user/profile and cloudId namespacing.
 * Phase 0: In-memory implementation; future phases add encrypted persistence.
 */
public interface TokenStore {

    Optional<StoredToken> get(String userProfileId, String cloudId);

    void save(String userProfileId, String cloudId, StoredToken token);

    void delete(String userProfileId, String cloudId);
}
