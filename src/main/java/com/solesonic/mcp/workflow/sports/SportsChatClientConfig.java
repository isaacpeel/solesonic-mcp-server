package com.solesonic.mcp.workflow.sports;

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
public class SportsChatClientConfig {
    public static final String OLLAMA_MODEL = "qwen3.5:9b";
    public static final String SPORTS_CHAT_CLIENT_GPU0 = "sports-chat-client-gpu0";
    public static final String SPORTS_CHAT_CLIENT_GPU1 = "sports-chat-client-gpu1";

    private final OllamaApi ollamaApi;

    public SportsChatClientConfig(OllamaApi ollamaApi) {
        this.ollamaApi = ollamaApi;
    }

    @Bean
    @Qualifier(SPORTS_CHAT_CLIENT_GPU0)
    public ChatClient sportsChatClientGPU0() {
        OllamaChatOptions ollamaChatOptions = OllamaChatOptions.builder()
                .model(OLLAMA_MODEL)
                .numThread(12)
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

    @Bean
    @Qualifier(SPORTS_CHAT_CLIENT_GPU1)
    public ChatClient sportsChatClientGPU1() {
        OllamaChatOptions ollamaChatOptions = OllamaChatOptions.builder()
                .model(OLLAMA_MODEL)
                .numThread(12)
                .numGPU(999)
                .mainGPU(1)
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
