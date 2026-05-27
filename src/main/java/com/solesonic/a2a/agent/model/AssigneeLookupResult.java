package com.solesonic.a2a.agent.model;

public record AssigneeLookupResult(boolean assigneeRequested,
                                   String assigneeId,
                                   String assigneeStatus,
                                   String assigneeName) {
}
