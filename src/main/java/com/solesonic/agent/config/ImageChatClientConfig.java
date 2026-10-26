package com.solesonic.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The image workflow's own client, used to write the image prompt. It runs on an uncensored model so
 * image prompts are not sanitized during the rewrite. Intent classification and workflow selection
 * use the decision model instead.
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
    public ChatClient imageChatClient(@Value("${solesonic.image.model}") String imageModel) {
        OpenAiChatOptions.Builder openAiChatOptions = OpenAiChatOptions.builder()
                .model(imageModel);

        return ChatClient.builder(openAiChatModel)
                .defaultOptions(openAiChatOptions)
                .build();
    }
}
