package com.solesonic.mcp.workflow.chain.step;

import com.solesonic.mcp.workflow.chain.UserStoryChainContext;
import com.solesonic.mcp.workflow.chain.UserStoryChainStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GenerateSummaryStep implements UserStoryChainStep {
    private static final Logger log = LoggerFactory.getLogger(GenerateSummaryStep.class);

    private static final String INPUT = "input";

    private final PromptTemplate summaryPromptTemplate;
    private final ChatClient chatClient;

    public GenerateSummaryStep(ChatClient chatClient,
                               @Value("classpath:prompt/user_story_summary_prompt.st") Resource userStorySummaryPrompt) {
        this.chatClient = chatClient;
        this.summaryPromptTemplate = new PromptTemplate(userStorySummaryPrompt);
    }

    @Override
    public void execute(UserStoryChainContext context, McpSyncRequestContext mcpSyncRequestContext) {
        log.info("execute GenerateSummaryStep");
        mcpSyncRequestContext.progress(p -> p.percentage(10).message("Creating story summary"));

        String detailedStory = context.getDetailedStory();
        Map<String, Object> summaryInputs = Map.of(INPUT, detailedStory);

        Prompt sunmmaryPrompt = summaryPromptTemplate.create(summaryInputs);

        String summary = chatClient.prompt(sunmmaryPrompt).call().content();

        context.setSummary(summary);
    }

    @Override
    public String name() {
        return "";
    }
}
