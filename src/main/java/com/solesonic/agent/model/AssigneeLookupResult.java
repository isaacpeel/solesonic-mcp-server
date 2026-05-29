package com.solesonic.agent.model;

public record AssigneeLookupResult(boolean assigneeRequested,
                                   String assigneeId,
                                   String assigneeStatus,
                                   String assigneeName) {
}
