package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import com.solesonic.mcp.exception.ToolFailures;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.agent.config.JiraChatClientConfig.USER_STORY_CHAT_CLIENT;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class GenerateStorySummaryNode implements AsyncNodeAction<JiraState> {

    private static final Logger log = LoggerFactory.getLogger(GenerateStorySummaryNode.class);

    private static final String OPERATION = "Generating the story summary";

    private static final String INPUT = "input";

    private static final double SUMMARY_TEMPERATURE = 0.2;
    private static final int SUMMARY_MAX_TOKENS = 32;

    private final ChatClient chatClient;
    private final PromptTemplate summaryPromptTemplate;

    public GenerateStorySummaryNode(
            @Qualifier(USER_STORY_CHAT_CLIENT) ChatClient chatClient,
            @Value("classpath:prompt/jira/user_story_summary_prompt.st") Resource userStorySummaryPrompt) {
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
            String summary = chatClient.prompt(summaryPrompt)
                    .options(OpenAiChatOptions.builder()
                            .temperature(SUMMARY_TEMPERATURE)
                            .maxTokens(SUMMARY_MAX_TOKENS))
                    .call()
                    .content();

            assert summary != null;
            return completedFuture(Map.of(JiraState.STORY_SUMMARY, summary));
        } catch (Exception exception) {
            log.error("Failed to generate story summary", exception);
            return failedFuture(ToolFailures.describe(OPERATION, exception));
        }
    }
}
