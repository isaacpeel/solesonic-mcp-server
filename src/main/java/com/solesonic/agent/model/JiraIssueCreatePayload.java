package com.solesonic.agent.model;

import java.io.Serializable;
import java.util.List;

public record JiraIssueCreatePayload(String summary,
                                     String description,
                                     List<String> acceptanceCriteria,
                                     AssigneeLookupResult assigneeLookupResult) implements Serializable {
}
