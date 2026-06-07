package com.solesonic.model.tavily;

import java.util.List;

public record TavilyExtractRequest(
    List<String> urls
) {}
