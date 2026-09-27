package com.solesonic.agent.config;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JiraChatClientConfig {

    private static final Logger log = LoggerFactory.getLogger(JiraChatClientConfig.class);

    public static final String USER_STORY_CHAT_CLIENT = "user-story-chat-client";

    public static final String JIRA_ASSIGNEE_CHAT_CLIENT = "jira-assignee-chat-client";

    public JiraChatClientConfig() {
    }


    @Bean
    @Qualifier(USER_STORY_CHAT_CLIENT)
    public ChatClient userStoryChatClient(@Value("${spring.ai.openai.api-key}") String apiKey,
                                               @Value("${spring.ai.openai.base-url}") String baseUrl,
                                               @Value("${solesonic.jira.model}") String jiraModel) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(jiraModel)
                .build();

        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .options(options)
                .build();

        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(new PromptLoggingAdvisor(USER_STORY_CHAT_CLIENT))
                .build();
    }

    @Bean
    @Qualifier(JIRA_ASSIGNEE_CHAT_CLIENT)
    public ChatClient jiraAssigneeChatClient(@Value("${spring.ai.openai.api-key}") String apiKey,
                                                  @Value("${spring.ai.openai.base-url}") String baseUrl,
                                                  @Value("${solesonic.jira.model}") String jiraModel) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(jiraModel)
                .build();

        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .options(options)
                .build();

        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(new PromptLoggingAdvisor(JIRA_ASSIGNEE_CHAT_CLIENT))
                .build();
    }

    private static final class PromptLoggingAdvisor implements CallAdvisor {

        private final String chatClientName;

        private PromptLoggingAdvisor(String chatClientName) {
            this.chatClientName = chatClientName;
        }

        @Override
        public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
            log.info("Sending prompt to model via {}", chatClientName);

            return callAdvisorChain.nextCall(chatClientRequest);
        }

        @Override
        public @NonNull String getName() {
            return chatClientName + "-prompt-logging-advisor";
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }
}
