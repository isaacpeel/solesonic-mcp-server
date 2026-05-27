package com.solesonic.a2a.workflow.agile;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgileChatClientConfig {
    public static final String OLLAMA_MODEL = "llama3.1:8b";
    public static final String AGILE_CHAT_CLIENT = "agile-chat-client";

    private final OllamaApi ollamaApi;
    private final ChatMemory chatMemory;

    public AgileChatClientConfig(OllamaApi ollamaApi, ChatMemory chatMemory) {
        this.ollamaApi = ollamaApi;
        this.chatMemory = chatMemory;
    }

    @Bean
    @Qualifier(AGILE_CHAT_CLIENT)
    public ChatClient agileChatClient() {
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

        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }
}
