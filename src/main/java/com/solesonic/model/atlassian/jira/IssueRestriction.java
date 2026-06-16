package com.solesonic.model.atlassian.jira;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssueRestriction(
        Map<String, Object> issuerestrictions,
        boolean shouldDisplay
) {}
