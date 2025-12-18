package com.solesonic.mcp.model.tavily;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TavilySearchResult(
    String title,
    String url,
    String content,
    Double score,
    @JsonProperty("raw_content") String rawContent,
    String favicon
) {}
