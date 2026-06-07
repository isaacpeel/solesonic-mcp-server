package com.solesonic.model.tavily;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TavilyExtractResponse(
    List<TavilyExtractResult> results,
    @JsonProperty("failed_results") List<TavilyFailedResult> failedResults
) {}
