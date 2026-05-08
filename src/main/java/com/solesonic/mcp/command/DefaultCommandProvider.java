package com.solesonic.mcp.command;

import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptProvider.COMMAND;

public class DefaultCommandProvider extends DefaultMetaProvider {

    public static final String DEFAULT = "default";

    @Override
    public Map<String, Object> getMeta() {
        return Map.of(COMMAND, DEFAULT);
    }
}
