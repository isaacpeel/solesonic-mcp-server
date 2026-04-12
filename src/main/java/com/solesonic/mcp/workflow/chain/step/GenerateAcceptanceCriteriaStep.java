package com.solesonic.mcp.workflow.chain.step;

import com.solesonic.mcp.workflow.WeightedProgressCoordinator;
import com.solesonic.mcp.workflow.chain.UserStoryChainContext;
import com.solesonic.mcp.workflow.chain.UserStoryChainStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GenerateAcceptanceCriteriaStep implements UserStoryChainStep {
    private static final Logger log = LoggerFactory.getLogger(GenerateAcceptanceCriteriaStep.class);

    private static final String USER_REQUEST = "user_request";
    private static final String USER_STORY = "user_story";
    private static final String FORMAT = "format";

    private final ChatClient chatClient;
    private final PromptTemplate acceptanceCriteriaPromptTemplate;

    public GenerateAcceptanceCriteriaStep(ChatClient chatClient,
                                          @Value("classpath:prompt/user_story_acceptance_criteria_prompt.st")
                                          Resource userStorySummaryPrompt) {
        this.chatClient = chatClient;
        acceptanceCriteriaPromptTemplate = new PromptTemplate(userStorySummaryPrompt);
    }

    @Override
    public void execute(UserStoryChainContext context, WeightedProgressCoordinator.TaskProgress taskProgress) {
        log.info("execute GenerateAcceptanceCriteriaStep");
        taskProgress.update(0.85, "Generating acceptance criteria");

        String rawRequest = context.getRawRequest();
        String summary = context.getSummary();

        ListOutputConverter listConverter = new ListOutputConverter(new DefaultConversionService());

        Map<String, Object> acceptanceCriteriaInputs = Map.of(
                USER_REQUEST, rawRequest,
                USER_STORY, summary,
                FORMAT, listConverter.getFormat());

        Prompt acceptanceCriteriaPrompt = acceptanceCriteriaPromptTemplate.create(acceptanceCriteriaInputs);

        List<String> acceptanceCriteria = chatClient.prompt(acceptanceCriteriaPrompt)
                .call()
                .entity(listConverter);

        context.setAcceptanceCriteria(acceptanceCriteria);
    }

    @Override
    public String name() {
        return "GenerateAcceptanceCriteriaStep";
    }
}