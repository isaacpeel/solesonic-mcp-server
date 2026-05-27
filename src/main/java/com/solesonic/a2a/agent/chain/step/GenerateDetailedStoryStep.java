package com.solesonic.a2a.agent.chain.step;

import com.solesonic.a2a.agent.chain.UserStoryChainContext;
import com.solesonic.a2a.agent.chain.UserStoryChainStep;
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
import java.util.Optional;

import static com.solesonic.a2a.agent.chain.UserStoryChainConfig.USER_STORY_CHAT_CLIENT;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
public class GenerateDetailedStoryStep implements UserStoryChainStep {
    private static final Logger log = LoggerFactory.getLogger(GenerateDetailedStoryStep.class);

    private static final String INPUT = "input";

    private final ChatClient chatClient;
    private final PromptTemplate descriptionPromptTemplate;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;

    public GenerateDetailedStoryStep(@Qualifier(USER_STORY_CHAT_CLIENT) ChatClient chatClient,
                                     @Value("classpath:prompt/user_story_description_prompt.st") Resource userStoryDescriptionPrompt,
                                     ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.descriptionPromptTemplate = new PromptTemplate(userStoryDescriptionPrompt);
        this.messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Override
    public void execute(UserStoryChainContext context, Optional<String> conversationId) {
        log.info("execute GenerateDetailedStoryStep");

        String rawRequest = context.getRawRequest();
        String renderedPrompt = descriptionPromptTemplate.render(Map.of(INPUT, rawRequest));

        String userStoryContent = chatClient.prompt()
                .user(renderedPrompt)
                .advisors(advisorSpec -> conversationId.ifPresent(id -> {
                    advisorSpec.advisors(messageChatMemoryAdvisor);
                    advisorSpec.param(CONVERSATION_ID, id);
                }))
                .call()
                .content();

        context.setDetailedStory(userStoryContent);
    }

    @Override
    public String name() {
        return "GenerateDetailedStoryStep";
    }
}
