package com.solesonic.mcp.workflow.chain;

import com.solesonic.mcp.workflow.chain.step.GenerateAcceptanceCriteriaStep;
import com.solesonic.mcp.workflow.chain.step.GenerateDetailedStoryStep;
import com.solesonic.mcp.workflow.chain.step.GenerateSummaryStep;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class UserStoryChainConfig {
    public static final String OLLAMA_MODEL = "mistral:7b";
    public static final String USER_STORY_CHAT_CLIENT = "user-story-chat-client";

    private final OllamaApi ollamaApi;

    public UserStoryChainConfig(OllamaApi ollamaApi) {
        this.ollamaApi = ollamaApi;
    }

    @Bean
    @Qualifier(USER_STORY_CHAT_CLIENT)
    public ChatClient userStoryChatClient() {
        OllamaChatOptions ollamaChatOptions = OllamaChatOptions.builder()
                .model(OLLAMA_MODEL)
                .build();

        ModelManagementOptions modelManagementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.WHEN_MISSING)
                .build();

        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                .defaultOptions(ollamaChatOptions)
                .ollamaApi(ollamaApi)
                .modelManagementOptions(modelManagementOptions)
                .build();

        return ChatClient.builder(ollamaChatModel)
                .build();
    }

    @Bean
    public UserStoryPromptChain userStoryPromptChain(
            GenerateDetailedStoryStep generateDetailedStoryStep,
            GenerateSummaryStep summarizeStoryStep,
            GenerateAcceptanceCriteriaStep generateAcceptanceCriteriaStep
    ) {
        return new UserStoryPromptChain(List.of(
                generateDetailedStoryStep,
                summarizeStoryStep,
                generateAcceptanceCriteriaStep
        ));
    }
}
