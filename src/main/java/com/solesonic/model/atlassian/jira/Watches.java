package com.solesonic.model.atlassian.jira;

public record Watches(
        String self,
        int watchCount,
        boolean isWatching
) {}

