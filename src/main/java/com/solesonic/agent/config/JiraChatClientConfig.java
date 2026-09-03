package com.solesonic.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JiraChatClientConfig {

    public static final String USER_STORY_MODEL = "qwen3.5-9b";
    public static final String USER_STORY_CHAT_CLIENT = "user-story-chat-client";

    private final OpenAiChatModel openAiChatModel;

    public JiraChatClientConfig(OpenAiChatModel openAiChatModel) {
        this.openAiChatModel = openAiChatModel;
    }

    @Bean
    @Qualifier(USER_STORY_CHAT_CLIENT)
    public ChatClient userStoryChatClient() {
        OpenAiChatOptions.Builder openAiChatOptions = OpenAiChatOptions.builder()
                .model(USER_STORY_MODEL);

        return ChatClient.builder(openAiChatModel)
                .defaultOptions(openAiChatOptions)
                .build();
    }
}
