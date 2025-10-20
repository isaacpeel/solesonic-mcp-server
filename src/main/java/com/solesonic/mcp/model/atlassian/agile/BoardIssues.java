package com.solesonic.mcp.model.atlassian.agile;

import java.util.List;

public record BoardIssues(String expand, Integer startAt, Integer maxResults, Integer total, List<BoardIssue> issues) {
}
