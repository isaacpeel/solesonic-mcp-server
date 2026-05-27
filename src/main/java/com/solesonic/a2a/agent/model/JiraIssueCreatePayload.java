package com.solesonic.a2a.agent.model;

import java.util.List;

public record JiraIssueCreatePayload(String summary, String description, List<String> acceptanceCriteria, AssigneeLookupResult assigneeLookupResult) {
}
