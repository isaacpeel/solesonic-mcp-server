package com.solesonic.agent.jira;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JiraChatClientConfig {

    public static final String OLLAMA_MODEL = "llama3.1:8b";
    public static final String USER_STORY_CHAT_CLIENT = "user-story-chat-client";

    private final OllamaApi ollamaApi;

    public JiraChatClientConfig(OllamaApi ollamaApi) {
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

        return ChatClient.builder(ollamaChatModel).build();
    }
}
