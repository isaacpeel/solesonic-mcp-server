package com.solesonic.mcp.workflow.chain.step;

import com.solesonic.mcp.workflow.WeightedProgressCoordinator;
import com.solesonic.mcp.workflow.chain.UserStoryChainContext;
import com.solesonic.mcp.workflow.chain.UserStoryChainStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.solesonic.mcp.workflow.chain.UserStoryChainConfig.USER_STORY_CHAT_CLIENT;

@Component
public class GenerateSummaryStep implements UserStoryChainStep {
    private static final Logger log = LoggerFactory.getLogger(GenerateSummaryStep.class);

    private static final String INPUT = "input";

    private final PromptTemplate summaryPromptTemplate;
    private final ChatClient chatClient;

    public GenerateSummaryStep(@Qualifier(USER_STORY_CHAT_CLIENT) ChatClient chatClient,
                               @Value("classpath:prompt/user_story_summary_prompt.st") Resource userStorySummaryPrompt) {
        this.chatClient = chatClient;
        this.summaryPromptTemplate = new PromptTemplate(userStorySummaryPrompt);
    }

    @Override
    public void execute(UserStoryChainContext context, WeightedProgressCoordinator.TaskProgress taskProgress) {
        log.info("execute GenerateSummaryStep");
        taskProgress.update(0.55, "Creating story summary");

        String detailedStory = context.getDetailedStory();
        Map<String, Object> summaryInputs = Map.of(INPUT, detailedStory);

        Prompt summaryPrompt = summaryPromptTemplate.create(summaryInputs);
        String summary = chatClient.prompt(summaryPrompt).call().content();

        context.setSummary(summary);
    }

    @Override
    public String name() {
        return "GenerateSummaryStep";
    }
}