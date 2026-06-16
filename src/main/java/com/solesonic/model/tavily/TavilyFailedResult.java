package com.solesonic.model.tavily;

public record TavilyFailedResult(
    String url,
    String error
) {}
