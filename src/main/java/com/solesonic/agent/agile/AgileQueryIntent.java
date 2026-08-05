package com.solesonic.agent.agile;

import java.io.Serializable;
import java.util.List;

/**
 * The parsed result of an agile intent extraction from a user's natural language request.
 *
 * @param issueKeys       Explicit issue keys the user named (e.g. ["IB-123"]). Empty list if none named.
 * @param assignee        Assignee to filter by: email, display name, or "currentUser()" for "my issues".
 *                        Null if not mentioned.
 * @param reporter        Reporter to filter by, same rules as assignee. Null if not mentioned.
 * @param olderThanDays   Age filter: issues created more than N days ago. Null if not mentioned.
 * @param supplementalJql Additional JQL for conditions not covered by the structured fields above
 *                        (status, labels, priority, issuetype, etc.). Empty string if none.
 * @param userIntent      "COUNT" (return a count),
 *                        "LIST" (return issue details),
 *                        or "TRANSITION" (change issue status).
 * @param startAt         The 0-based index to start from, extracted from page or offset language in the
 *                        user's request. Null when the user did not mention a specific page or offset
 *                        (defaults to 0).
 * @param targetStatus    The destination status name when userIntent is "TRANSITION"
 *                        (e.g. "Done", "In Progress"). Null for non-transition queries.
 */
public record AgileQueryIntent(
        List<String> issueKeys,
        String assignee,
        String reporter,
        Integer olderThanDays,
        String supplementalJql,
        String userIntent,
        Integer startAt,
        String targetStatus
) implements Serializable {

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

    public boolean hasExplicitScope() {
        return (issueKeys != null && !issueKeys.isEmpty())
                || (assignee != null && !assignee.isBlank())
                || (reporter != null && !reporter.isBlank())
                || olderThanDays != null
                || (supplementalJql != null && !supplementalJql.isBlank());
    }
}
