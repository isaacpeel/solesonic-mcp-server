package com.solesonic.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgileChatClientConfig {
    public static final String AGILE_MODEL = "qwen3.5-9b";
    public static final String AGILE_CHAT_CLIENT = "agile-chat-client";

    private final OpenAiChatModel openAiChatModel;

    public AgileChatClientConfig(OpenAiChatModel openAiChatModel) {
        this.openAiChatModel = openAiChatModel;
    }

    @Bean
    @Qualifier(AGILE_CHAT_CLIENT)
    public ChatClient agileChatClient() {
        OpenAiChatOptions.Builder openAiChatOptions = OpenAiChatOptions.builder()
                .model(AGILE_MODEL);

        return ChatClient.builder(openAiChatModel)
                .defaultOptions(openAiChatOptions)
                .build();
    }
}
