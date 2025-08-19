package com.solesonic.mcp.model.atlassian.jira;

import java.util.Map;

public record IssueRestriction(
        Map<String, Object> issuerestrictions,
        boolean shouldDisplay
) {}
