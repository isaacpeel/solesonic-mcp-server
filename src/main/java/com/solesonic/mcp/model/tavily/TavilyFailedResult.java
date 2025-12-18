package com.solesonic.mcp.model.tavily;

public record TavilyFailedResult(
    String url,
    String error
) {}
