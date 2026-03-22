package com.solesonic.mcp.prompt;

import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptProvider.COMMAND;

public class ConfluenceCommandProvider extends DefaultMetaProvider {

    public static final String CONFLUENCE = "confluence";

    @Override
    public Map<String, Object> getMeta() {
        return Map.of(COMMAND, CONFLUENCE);
    }
}
