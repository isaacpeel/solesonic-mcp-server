package com.solesonic.a2a.workflow.model;

public record AssigneeLookupResult(boolean assigneeRequested,
                                   String assigneeId,
                                   String assigneeStatus,
                                   String assigneeName) {
}
