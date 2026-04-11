package com.solesonic.mcp.workflow.chain;

import com.solesonic.mcp.workflow.WeightedProgressCoordinator;

public interface UserStoryChainStep {
    void execute(UserStoryChainContext context, WeightedProgressCoordinator.TaskProgress taskProgress);
    String name();
}