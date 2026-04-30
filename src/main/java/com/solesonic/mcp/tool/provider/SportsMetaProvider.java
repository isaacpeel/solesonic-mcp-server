package com.solesonic.mcp.tool.provider;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptProvider.COMMAND;
import static com.solesonic.mcp.tool.sports.SportsResearchTools.SPORTS_WORKFLOW;

@SuppressWarnings("unused")
public class SportsMetaProvider implements MetaProvider {
    @Override
    public Map<String, Object> getMeta() {
        return Map.of(
                COMMAND, "sports",
                "task", SPORTS_WORKFLOW);
    }
}
