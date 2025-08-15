package com.solesonic.mcp.jira.model;

/**
 * Input contract for search_assignable_users.
 */
public record SearchAssignableUsersRequest(
        String query
) {}
