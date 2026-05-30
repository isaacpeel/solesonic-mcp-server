package com.solesonic.agent.sports;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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
    private final ChatMemory chatMemory;

    public SportsChatClientConfig(OllamaApi ollamaApi, ChatMemory chatMemory) {
        this.ollamaApi = ollamaApi;
        this.chatMemory = chatMemory;
    }

    @Bean(SPORTS_CHAT_CLIENT)
    public ChatClient sportsClient() {
        OllamaChatOptions ollamaChatOptions = OllamaChatOptions.builder()
                .model(OLLAMA_MODEL)
                .numThread(8)
                .numGPU(999)
                .mainGPU(1)
                .numCtx(16384)
                .numBatch(1024)
                .disableThinking()
                .build();

        ModelManagementOptions modelManagementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.WHEN_MISSING)
                .build();

        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                .defaultOptions(ollamaChatOptions)
                .ollamaApi(ollamaApi)
                .modelManagementOptions(modelManagementOptions)
                .build();

        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .build();

        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }

    @Bean(SPORTS_INTENT_CLIENT)
    public ChatClient sportsIntentClient() {
        OllamaChatOptions ollamaChatOptions = OllamaChatOptions.builder()
                .model(SPORTS_INTENT_MODEL)
                .numThread(0)
                .numGPU(999)
                .mainGPU(0)
                .numCtx(4096)
                .numBatch(1024)
                .disableThinking()
                .build();

        ModelManagementOptions modelManagementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.WHEN_MISSING)
                .build();

        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                .defaultOptions(ollamaChatOptions)
                .ollamaApi(ollamaApi)
                .modelManagementOptions(modelManagementOptions)
                .build();

        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .build();

        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }
}
