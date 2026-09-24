package com.solesonic.agent.model;

import com.solesonic.model.atlassian.jira.User;

import java.io.Serializable;

/**
 * A Jira user the story could be assigned to. Kept to the two fields the assignee picker needs so
 * it can travel on {@code JiraState}, which requires serializable values.
 */
public record AssigneeCandidate(String accountId, String displayName) implements Serializable {

    public static AssigneeCandidate from(User user) {
        return new AssigneeCandidate(user.accountId(), user.displayName());
    }

    /**
     * The name shown to the user, falling back to the account id when Jira returns no display name.
     */
    public String label() {
        if (displayName == null || displayName.isBlank()) {
            return accountId;
        }

        return displayName;
    }
}
