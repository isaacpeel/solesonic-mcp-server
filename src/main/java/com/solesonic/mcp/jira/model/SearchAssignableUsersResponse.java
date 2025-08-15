package com.solesonic.mcp.jira.model;

import java.util.List;

/**
 * Output contract for search_assignable_users.
 */
public record SearchAssignableUsersResponse(
        List<User> users
) {
    public record User(
            String accountId,
            String displayName,
            String emailAddress
    ) {}
}
