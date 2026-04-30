package com.solesonic.mcp.command;

import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;

import java.util.List;
import java.util.Map;

import static com.solesonic.mcp.prompt.PromptProvider.COMMAND;
import static com.solesonic.mcp.tool.sports.SportsResearchTools.SPORTS_RESEARCH;

public class SportsCommandProvider extends DefaultMetaProvider {

    public static final String SPORTS = "sportsball";
    public static final String TOOLS = "tools";

    @Override
    public Map<String, Object> getMeta() {
        return Map.of(COMMAND, SPORTS, TOOLS, List.of(SPORTS_RESEARCH));
    }
}
