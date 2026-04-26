package com.solesonic.mcp.workflow.jira.skill;

import com.solesonic.mcp.workflow.chain.UserStoryChainContext;
import com.solesonic.mcp.workflow.chain.UserStoryPromptChain;
import com.solesonic.mcp.workflow.framework.WorkflowProgressTracker;
import org.springframework.stereotype.Component;

@Component
public class UserStoryGenerationSkill {
    private final UserStoryPromptChain userStoryPromptChain;

    public UserStoryGenerationSkill(UserStoryPromptChain userStoryPromptChain) {
        this.userStoryPromptChain = userStoryPromptChain;
    }

    public UserStoryChainContext generate(String rawUserRequest, WorkflowProgressTracker.StepProgress stepProgress) {
        return userStoryPromptChain.run(rawUserRequest, stepProgress);
    }
}
