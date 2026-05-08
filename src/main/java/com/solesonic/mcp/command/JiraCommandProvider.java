package com.solesonic.mcp.command;

import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptProvider.COMMAND;

public class JiraCommandProvider extends DefaultMetaProvider {

    public static final String JIRA = "jira";

    @Override
    public Map<String, Object> getMeta() {
        return Map.of(COMMAND, JIRA);
    }
}
