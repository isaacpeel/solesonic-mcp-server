package com.solesonic.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.solesonic.agent.config.AgileChatClientConfig.AGILE_MODEL;

/**
 * The image workflow router's own client. It deliberately runs on the agile model, so changing that
 * model stays a one-line change.
 */
@Configuration
public class ImageChatClientConfig {
    public static final String IMAGE_CHAT_CLIENT = "image-chat-client";

    private final OpenAiChatModel openAiChatModel;

    public ImageChatClientConfig(OpenAiChatModel openAiChatModel) {
        this.openAiChatModel = openAiChatModel;
    }

    @Bean
    @Qualifier(IMAGE_CHAT_CLIENT)
    public ChatClient imageChatClient() {
        OpenAiChatOptions.Builder openAiChatOptions = OpenAiChatOptions.builder()
                .model(AGILE_MODEL);

        return ChatClient.builder(openAiChatModel)
                .defaultOptions(openAiChatOptions)
                .build();
    }
}
