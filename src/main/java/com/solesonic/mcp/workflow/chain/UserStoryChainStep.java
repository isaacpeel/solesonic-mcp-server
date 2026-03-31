package com.solesonic.mcp.workflow.chain;

public interface UserStoryChainStep {
    void execute(UserStoryChainContext context);
    String name();
}
