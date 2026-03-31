package com.solesonic.mcp.workflow.chain.step;

import com.solesonic.mcp.workflow.chain.UserStoryChainContext;
import com.solesonic.mcp.workflow.chain.UserStoryChainStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GenerateDetailedStoryStep implements UserStoryChainStep {
    private static final Logger log = LoggerFactory.getLogger(GenerateDetailedStoryStep.class);

    private static final String INPUT = "input";

    private final PromptTemplate descriptionPromptTemplate;
    private final ChatClient chatClient;

    public GenerateDetailedStoryStep(ChatClient chatClient,
                                     @Value("classpath:prompt/user_story_description_prompt.st") Resource userStoryDescriptionPrompt) {
        this.chatClient = chatClient;
        this.descriptionPromptTemplate = new PromptTemplate(userStoryDescriptionPrompt);
    }

    @Override
    public void execute(UserStoryChainContext context) {
        log.info("execute GenerateDetailedStoryStep");

        String rawRequest = context.getRawRequest();
        Map<String, Object> descriptionInputs = Map.of(INPUT, rawRequest);

        Prompt descriptionPrompt = descriptionPromptTemplate.create(descriptionInputs);

        String userStoryContent = chatClient.prompt(descriptionPrompt)
                .call()
                .content();

        context.setDetailedStory(userStoryContent);
    }


    @Override
    public String name() {
        return "GenerateDetailedStoryStep";
    }
}
