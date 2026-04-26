package com.solesonic.mcp.workflow.chain;

import com.solesonic.mcp.workflow.WeightedProgressCoordinator;

import java.util.List;

public class UserStoryPromptChain {
    private final List<UserStoryChainStep> steps;

    public UserStoryPromptChain(List<UserStoryChainStep> steps) {
        this.steps = steps;
    }

    public UserStoryChainContext run(String rawRequest, WeightedProgressCoordinator.TaskProgress taskProgress) {
        UserStoryChainContext context = new UserStoryChainContext(rawRequest);

        for (UserStoryChainStep step : steps) {
            step.execute(context, taskProgress);
        }

        taskProgress.done("User story generated");
        return context;
    }
}
