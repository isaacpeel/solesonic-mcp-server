package com.solesonic.mcp.model.atlassian.jira;

@SuppressWarnings("unused")
public record Watchers(
        String self,
        int watchCount,
        boolean isWatching
) {}
