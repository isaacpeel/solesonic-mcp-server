package com.solesonic.mcp.model.tavily;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TavilySearchResponse(
    String query,
    String answer,
    List<TavilySearchResult> results,
    @JsonProperty("response_time") String responseTime,
    @JsonProperty("request_id") String requestId,
    List<TavilyImage> images
) {}
