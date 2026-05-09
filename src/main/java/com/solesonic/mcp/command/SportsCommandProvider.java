package com.solesonic.mcp.command;

import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptProvider.COMMAND;

public class SportsCommandProvider extends DefaultMetaProvider {

    public static final String SPORTS = "sportsball";

    @Override
    public Map<String, Object> getMeta() {
        return Map.of(COMMAND, SPORTS);
    }
}
