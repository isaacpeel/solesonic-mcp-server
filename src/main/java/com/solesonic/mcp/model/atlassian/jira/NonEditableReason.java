package com.solesonic.mcp.model.atlassian.jira;

public record NonEditableReason(
        String reason,
        String message
) {}
