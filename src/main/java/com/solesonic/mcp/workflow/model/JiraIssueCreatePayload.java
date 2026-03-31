package com.solesonic.mcp.workflow.model;

import java.util.List;

public record JiraIssueCreatePayload(String summary, String description, List<String> acceptanceCriteria, String assigneeId) {
}
