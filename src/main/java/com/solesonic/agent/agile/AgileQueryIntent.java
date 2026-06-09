package com.solesonic.agent.agile;

import java.io.Serializable;

/**
 * The parsed result of an agile intent extraction from a user's natural language request.
 *
 * @param jqlFilter    A JQL expression to filter issues, or an empty string to return all issues.
 * @param userIntent    "COUNT" (return a count),
 *                     "LIST" (return issue details),
 *                     or "TRANSITION" (change issue status).
 * @param startAt      The 0-based index to start from, extracted from page or offset language in the user's request.
 *                     Null when the user did not mention a specific page or offset (defaults to 0).
 * @param targetStatus The destination status name when userIntent is "TRANSITION" (e.g. "Done", "In Progress").
 *                     Null for non-transition queries.
 */
public record AgileQueryIntent(String jqlFilter,
                               String userIntent,
                               Integer startAt,
                               String targetStatus) implements Serializable {

    public static final String COUNT = "COUNT";
    public static final String TRANSITION = "TRANSITION";

    @SuppressWarnings("unused")
    public boolean isCountQuery() {
        return COUNT.equalsIgnoreCase(userIntent);
    }

    public boolean isTransitionQuery() {
        return TRANSITION.equalsIgnoreCase(userIntent);
    }

    public int resolvedStartAt() {
        return startAt != null ? startAt : 0;
    }
}
