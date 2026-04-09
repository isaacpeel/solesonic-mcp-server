package com.solesonic.mcp.workflow.chain;

import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import java.util.List;

public class UserStoryPromptChain {
    private final List<UserStoryChainStep> steps;

    public UserStoryPromptChain(List<UserStoryChainStep> steps) {
        this.steps = steps;
    }

    public UserStoryChainContext run(String rawRequest, McpSyncRequestContext mcpSyncRequestContext) {
        UserStoryChainContext context = new UserStoryChainContext(rawRequest);

        for (UserStoryChainStep step : steps) {
            step.execute(context, mcpSyncRequestContext);
        }

        return context;
    }
}
