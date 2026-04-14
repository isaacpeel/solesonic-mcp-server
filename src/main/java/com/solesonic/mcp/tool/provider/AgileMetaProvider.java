package com.solesonic.mcp.tool.provider;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptProvider.COMMAND;
import static com.solesonic.mcp.tool.atlassian.JiraAgileTools.AGILE_WORKFLOW;

public class AgileMetaProvider implements MetaProvider {



    @Override
    public Map<String, Object> getMeta() {
        return Map.of(
                COMMAND, "agile",
                "task", AGILE_WORKFLOW);
    }
}
