package com.solesonic.mcp.workflow.chain;

import java.util.List;

public class UserStoryPromptChain {
    private final List<UserStoryChainStep> steps;

    public UserStoryPromptChain(List<UserStoryChainStep> steps) {
        this.steps = steps;
    }

    public UserStoryChainContext run(String rawRequest) {
        UserStoryChainContext context = new UserStoryChainContext(rawRequest);

        for (UserStoryChainStep step : steps) {
            step.execute(context);
        }

        return context;
    }
}
