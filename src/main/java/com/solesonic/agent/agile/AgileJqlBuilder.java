package com.solesonic.agent.agile;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles a JQL expression from the structured fields of an {@link AgileQueryIntent}.
 * This is the single place where structured entity extraction becomes JQL syntax,
 * replacing ad-hoc string reads of a raw jqlFilter field scattered across service classes.
 */
public final class AgileJqlBuilder {

    private AgileJqlBuilder() {}

    /**
     * Builds a JQL expression from the structured fields of the given intent.
     * Returns an empty string if no scope fields are set (meaning "all issues").
     */
    public static String build(AgileQueryIntent queryIntent) {
        List<String> clauses = new ArrayList<>();

        if (queryIntent.issueKeys() != null && !queryIntent.issueKeys().isEmpty()) {
            if (queryIntent.issueKeys().size() == 1) {
                clauses.add("key = " + queryIntent.issueKeys().getFirst());
            } else {
                clauses.add("key in (" + String.join(", ", queryIntent.issueKeys()) + ")");
            }
        }

        if (queryIntent.assignee() != null && !queryIntent.assignee().isBlank()) {
            clauses.add("assignee = " + quoteOrToken(queryIntent.assignee()));
        }

        if (queryIntent.reporter() != null && !queryIntent.reporter().isBlank()) {
            clauses.add("reporter = " + quoteOrToken(queryIntent.reporter()));
        }

        if (queryIntent.olderThanDays() != null) {
            clauses.add("created <= \"-" + queryIntent.olderThanDays() + "d\"");
        }

        if (queryIntent.supplementalJql() != null && !queryIntent.supplementalJql().isBlank()) {
            clauses.add("(" + queryIntent.supplementalJql().strip() + ")");
        }

        return String.join(" AND ", clauses);
    }

    /**
     * JQL function tokens like "currentUser()" pass through unquoted.
     * Everything else (account id, email, display name) gets double-quoted.
     */
    private static String quoteOrToken(String value) {
        return value.endsWith("()") ? value : "\"" + value + "\"";
    }
}
