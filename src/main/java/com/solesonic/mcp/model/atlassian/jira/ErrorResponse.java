package com.solesonic.mcp.model.atlassian.jira;

import java.util.List;

@SuppressWarnings("unused")
public record ErrorResponse(List<String> errorMessages, String errors, Integer status) {
}
