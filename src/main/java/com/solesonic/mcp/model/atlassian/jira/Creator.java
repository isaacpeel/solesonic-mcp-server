package com.solesonic.mcp.model.atlassian.jira;

@SuppressWarnings("unused")
public record Creator(
        String self,
        String accountId,
        String emailAddress,
        AvatarUrls avatarUrls,
        String displayName,
        boolean active,
        String timeZone,
        String accountType
) {}
