package com.solesonic.mcp.jira.auth;

/**
 * Resolves the current MCP user/profile identifier for namespacing tokens and caches.
 * For now, uses the MCP_PROFILE environment variable or falls back to "default".
 */
public class UserProfileResolver {
    public String currentProfileId() {
        String v = System.getenv("MCP_PROFILE");
        if (v == null || v.isBlank()) {
            v = System.getProperty("mcp.profile", "default");
        }
        return v;
    }
}
