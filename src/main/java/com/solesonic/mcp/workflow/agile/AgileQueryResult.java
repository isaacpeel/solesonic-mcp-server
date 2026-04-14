package com.solesonic.mcp.workflow.agile;

/**
 * The parsed result of an agile intent extraction from a user's natural language request.
 *
 * @param jqlFilter  A JQL expression to filter issues, or an empty string to return all issues.
 * @param queryType  Either "COUNT" (return a count of matching issues) or "LIST" (return issue details).
 * @param startAt    The 0-based index to start from, extracted from page or offset language in the user's request.
 *                   Null when the user did not mention a specific page or offset (defaults to 0).
 */
public record AgileQueryResult(String jqlFilter, String queryType, Integer startAt) {

    public boolean isCountQuery() {
        return "COUNT".equalsIgnoreCase(queryType);
    }

    public int resolvedStartAt() {
        return startAt != null ? startAt : 0;
    }
}
