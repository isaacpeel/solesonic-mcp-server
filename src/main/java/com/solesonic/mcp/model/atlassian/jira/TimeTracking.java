package com.solesonic.mcp.model.atlassian.jira;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimeTracking(
) {}
