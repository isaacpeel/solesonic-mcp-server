package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.a2a.config.jira.JiraChatClientConfig.USER_STORY_CHAT_CLIENT;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class GenerateStorySummaryNode implements AsyncNodeAction<JiraState> {

    private static final Logger log = LoggerFactory.getLogger(GenerateStorySummaryNode.class);

    private static final String INPUT = "input";

    private final ChatClient chatClient;
    private final PromptTemplate summaryPromptTemplate;

    public GenerateStorySummaryNode(
            @Qualifier(USER_STORY_CHAT_CLIENT) ChatClient chatClient,
            @Value("classpath:prompt/user_story_summary_prompt.st") Resource userStorySummaryPrompt) {
        this.chatClient = chatClient;
        this.summaryPromptTemplate = new PromptTemplate(userStorySummaryPrompt);
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(JiraState state) {
        try {
            String detailedDescription = state.detailedDescription().orElseThrow(() ->
                    new IllegalStateException("detailedDescription is required"));

            log.info("Generating story summary");

            Prompt summaryPrompt = summaryPromptTemplate.create(Map.of(INPUT, detailedDescription));
            String summary = chatClient.prompt(summaryPrompt).call().content();

            assert summary != null;
            return completedFuture(Map.of(JiraState.STORY_SUMMARY, summary));
        } catch (Exception exception) {
            log.error("Failed to generate story summary", exception);
            return failedFuture(exception);
        }
    }
}
