package com.solesonic.a2a.workflow.sports;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SportsChatClientConfig {
    public static final String OLLAMA_MODEL = "qwen3.5:9b";
    public static final String SPORTS_INTENT_MODEL = "granite4.1:3b";

    public static final String SPORTS_CHAT_CLIENT = "sports-chat-client";
    public static final String SPORTS_INTENT_CLIENT = "sports-intent-client";

    private final OllamaApi ollamaApi;

    public SportsChatClientConfig(OllamaApi ollamaApi) {
        this.ollamaApi = ollamaApi;
    }

    @Bean(SPORTS_CHAT_CLIENT)
    public ChatClient sportsClient() {
        OllamaChatOptions ollamaChatOptions = OllamaChatOptions.builder()
                .model(OLLAMA_MODEL)
                .numThread(24)
                .numGPU(999)
                .mainGPU(0)
                .numCtx(131072)
                .numBatch(1024)
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

    @Bean(SPORTS_INTENT_CLIENT)
    public ChatClient sportsIntentClient() {
        OllamaChatOptions ollamaChatOptions = OllamaChatOptions.builder()
                .model(SPORTS_INTENT_MODEL)
                .numThread(24)
                .numGPU(999)
                .mainGPU(0)
                .numCtx(131072)
                .numBatch(1024)
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
}
