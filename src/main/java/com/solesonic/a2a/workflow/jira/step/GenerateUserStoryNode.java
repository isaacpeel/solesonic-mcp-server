package com.solesonic.a2a.workflow.jira.step;

import com.solesonic.a2a.workflow.chain.UserStoryChainContext;
import com.solesonic.a2a.workflow.chain.UserStoryPromptChain;
import com.solesonic.a2a.workflow.jira.JiraState;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class GenerateUserStoryNode implements AsyncNodeAction<JiraState> {

    private static final Logger log = LoggerFactory.getLogger(GenerateUserStoryNode.class);

    private final UserStoryPromptChain userStoryPromptChain;

    public GenerateUserStoryNode(UserStoryPromptChain userStoryPromptChain) {
        this.userStoryPromptChain = userStoryPromptChain;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(JiraState state) {
        try {
            String userMessage = state.userMessage().orElseThrow(() ->
                    new IllegalStateException("userMessage is required"));

            log.info("Generating user story for: {}", userMessage);

            UserStoryChainContext chainContext = userStoryPromptChain.run(userMessage, state.conversationId());

            return completedFuture(Map.of(
                    JiraState.STORY_SUMMARY, chainContext.getSummary(),
                    JiraState.DETAILED_DESCRIPTION, chainContext.getDetailedStory(),
                    JiraState.ACCEPTANCE_CRITERIA, chainContext.getAcceptanceCriteria()
            ));
        } catch (Exception exception) {
            log.error("Failed to generate user story", exception);
            return failedFuture(exception);
        }
    }
}
