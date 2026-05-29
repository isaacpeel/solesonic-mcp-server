package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.agent.jira.JiraChatClientConfig.USER_STORY_CHAT_CLIENT;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
public class GenerateDetailedDescriptionNode implements AsyncNodeAction<JiraState> {

    private static final Logger log = LoggerFactory.getLogger(GenerateDetailedDescriptionNode.class);

    private static final String INPUT = "input";

    private final ChatClient chatClient;
    private final PromptTemplate descriptionPromptTemplate;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;

    public GenerateDetailedDescriptionNode(
            @Qualifier(USER_STORY_CHAT_CLIENT) ChatClient chatClient,
            @Value("classpath:prompt/user_story_description_prompt.st") Resource userStoryDescriptionPrompt,
            ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.descriptionPromptTemplate = new PromptTemplate(userStoryDescriptionPrompt);
        this.messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(JiraState state) {
        try {
            String userMessage = state.userMessage().orElseThrow(() ->
                    new IllegalStateException("userMessage is required"));

            log.info("Generating detailed description for: {}", userMessage);

            String renderedPrompt = descriptionPromptTemplate.render(Map.of(INPUT, userMessage));

            String detailedDescription = chatClient.prompt()
                    .user(renderedPrompt)
                    .advisors(advisorSpec -> state.conversationId().ifPresent(conversationId -> {
                        advisorSpec.advisors(messageChatMemoryAdvisor);
                        advisorSpec.param(CONVERSATION_ID, conversationId);
                    }))
                    .call()
                    .content();

            assert detailedDescription != null;
            return completedFuture(Map.of(JiraState.DETAILED_DESCRIPTION, detailedDescription));
        } catch (Exception exception) {
            log.error("Failed to generate detailed description", exception);
            return failedFuture(exception);
        }
    }
}
