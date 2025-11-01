package com.solesonic.mcp.model.atlassian.jira;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AvatarUrls(
        String _48x48,
        String _24x24,
        String _16x16,
        String _32x32
) {}
