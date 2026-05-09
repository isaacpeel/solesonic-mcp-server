package com.solesonic.mcp.tool.provider;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptProvider.COMMAND;

@SuppressWarnings("unused")
public class SportsMetaProvider implements MetaProvider {
    @Override
    public Map<String, Object> getMeta() {
        return Map.of(COMMAND, "sports");
    }
}
