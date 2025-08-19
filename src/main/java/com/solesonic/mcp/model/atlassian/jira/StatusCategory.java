package com.solesonic.mcp.model.atlassian.jira;

public record StatusCategory(
        String self,
        int id,
        String key,
        String colorName,
        String name
) {}
