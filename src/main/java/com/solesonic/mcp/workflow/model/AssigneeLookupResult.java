package com.solesonic.mcp.workflow.model;

public record AssigneeLookupResult(boolean assigneeRequested,
                                   String assigneeId,
                                   String assigneeStatus,
                                   String assigneeName) {
}
