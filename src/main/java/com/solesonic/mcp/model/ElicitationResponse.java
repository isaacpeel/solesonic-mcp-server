package com.solesonic.mcp.model;

import org.springframework.ai.mcp.annotation.McpToolParam;

@SuppressWarnings("unused")
public record ElicitationResponse(
        @McpToolParam(required = false, description = "The user's response value when input beyond confirmation is needed (e.g. an ID, a name, or a selection).")
        String value
) {}
