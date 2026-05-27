package com.solesonic.a2a.workflow.chain;

import java.util.List;
import java.util.Optional;

public class UserStoryPromptChain {
    private final List<UserStoryChainStep> steps;

    public UserStoryPromptChain(List<UserStoryChainStep> steps) {
        this.steps = steps;
    }

    public UserStoryChainContext run(String rawRequest, Optional<String> conversationId) {
        UserStoryChainContext context = new UserStoryChainContext(rawRequest);

        for (UserStoryChainStep step : steps) {
            step.execute(context, conversationId);
        }

        return context;
    }
}
