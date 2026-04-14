package com.solesonic.mcp.workflow.agile;

/**
 * The parsed result of an agile intent extraction from a user's natural language request.
 *
 * @param jqlFilter  A JQL expression to filter issues, or an empty string to return all issues.
 * @param queryType  Either "COUNT" (return a count of matching issues) or "LIST" (return issue details).
 */
public record AgileQueryResult(String jqlFilter, String queryType) {

    public boolean isCountQuery() {
        return "COUNT".equalsIgnoreCase(queryType);
    }
}
