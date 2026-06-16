package com.solesonic.model.atlassian.jira;

public record Priority(
        String self,
        String iconUrl,
        String name,
        String id
) {}
