package com.solesonic.a2a.workflow.chain;

import java.util.Optional;

public interface UserStoryChainStep {
    void execute(UserStoryChainContext context, Optional<String> conversationId);
    String name();
}
