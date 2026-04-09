package com.solesonic.mcp.workflow.chain;

import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

public interface UserStoryChainStep {
    void execute(UserStoryChainContext context, McpSyncRequestContext mcpSyncRequestContext);
    String name();
}
