package com.solesonic.mcp.workflow.jira.skill;

import com.solesonic.mcp.workflow.chain.UserStoryChainContext;
import com.solesonic.mcp.workflow.chain.UserStoryPromptChain;
import com.solesonic.mcp.workflow.framework.WorkflowProgressTracker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class UserStoryGenerationSkill {
    private final UserStoryPromptChain userStoryPromptChain;

    public UserStoryGenerationSkill(UserStoryPromptChain userStoryPromptChain) {
        this.userStoryPromptChain = userStoryPromptChain;
    }

    public Mono<UserStoryChainContext> generate(String rawUserRequest, WorkflowProgressTracker.StepProgress stepProgress) {
        return userStoryPromptChain.run(rawUserRequest, stepProgress);
    }
}
