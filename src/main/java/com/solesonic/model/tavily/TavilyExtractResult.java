package com.solesonic.model.tavily;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TavilyExtractResult(
    String url,
    @JsonProperty("raw_content") String rawContent
) {}
