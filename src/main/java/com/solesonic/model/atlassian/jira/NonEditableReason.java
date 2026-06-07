package com.solesonic.model.atlassian.jira;

public record NonEditableReason(
        String reason,
        String message
) {}
