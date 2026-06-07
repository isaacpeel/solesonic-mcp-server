package com.solesonic.model.atlassian.jira;

public record Status(
        String self,
        String description,
        String iconUrl,
        String name,
        String id,
        StatusCategory statusCategory
) {}
