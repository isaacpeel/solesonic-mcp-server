package com.solesonic.a2a.workflow.chain;

import com.solesonic.a2a.workflow.WeightedProgressCoordinator;

public interface UserStoryChainStep {
    void execute(UserStoryChainContext context, WeightedProgressCoordinator.TaskProgress taskProgress);
    String name();
}