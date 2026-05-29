package com.solesonic.agent.model;

import java.io.Serializable;

public record AssigneeLookupResult(boolean assigneeRequested,
                                   String assigneeId,
                                   String assigneeStatus,
                                   String assigneeName) implements Serializable {
}
