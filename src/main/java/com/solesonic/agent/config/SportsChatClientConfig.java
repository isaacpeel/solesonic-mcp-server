package com.solesonic.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SportsChatClientConfig {
    public static final String SPORTS_CHAT_CLIENT = "sports-chat-client";
    public static final String SPORTS_INTENT_CLIENT = "sports-intent-client";

    private final OpenAiChatModel openAiChatModel;
    private final ChatMemory chatMemory;

    public SportsChatClientConfig(OpenAiChatModel openAiChatModel, ChatMemory chatMemory) {
        this.openAiChatModel = openAiChatModel;
        this.chatMemory = chatMemory;
    }

    @Bean(SPORTS_CHAT_CLIENT)
    public ChatClient sportsClient(@Value("${solesonic.sports.chat-model}") String sportsChatModel) {
        OpenAiChatOptions.Builder openAiChatOptions = OpenAiChatOptions.builder()
                .model(sportsChatModel);

        return ChatClient.builder(openAiChatModel)
                .defaultOptions(openAiChatOptions)
                .build();
    }

    @Bean(SPORTS_INTENT_CLIENT)
    public ChatClient sportsIntentClient(@Value("${solesonic.sports.intent-model}") String sportsIntentModel) {
        OpenAiChatOptions.Builder openAiChatOptions = OpenAiChatOptions.builder()
                .model(sportsIntentModel);

        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .build();

        return ChatClient.builder(openAiChatModel)
                .defaultOptions(openAiChatOptions)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }
}
