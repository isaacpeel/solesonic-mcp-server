package com.solesonic.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SportsChatClientConfig {
    public static final String SPORTS_CHAT_MODEL = "qwen3.5-9b";
    public static final String SPORTS_INTENT_MODEL = "qwen3.5-9b";

    public static final String SPORTS_CHAT_CLIENT = "sports-chat-client";
    public static final String SPORTS_INTENT_CLIENT = "sports-intent-client";

    private final OpenAiChatModel openAiChatModel;
    private final ChatMemory chatMemory;

    public SportsChatClientConfig(OpenAiChatModel openAiChatModel, ChatMemory chatMemory) {
        this.openAiChatModel = openAiChatModel;
        this.chatMemory = chatMemory;
    }

    @Bean(SPORTS_CHAT_CLIENT)
    public ChatClient sportsClient() {
        OpenAiChatOptions.Builder openAiChatOptions = OpenAiChatOptions.builder()
                .model(SPORTS_CHAT_MODEL);

        return ChatClient.builder(openAiChatModel)
                .defaultOptions(openAiChatOptions)
                .build();
    }

    @Bean(SPORTS_INTENT_CLIENT)
    public ChatClient sportsIntentClient() {
        OpenAiChatOptions.Builder openAiChatOptions = OpenAiChatOptions.builder()
                .model(SPORTS_INTENT_MODEL);

        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .build();

        return ChatClient.builder(openAiChatModel)
                .defaultOptions(openAiChatOptions)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }
}
