package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.agent.config.JiraChatClientConfig.USER_STORY_CHAT_CLIENT;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class GenerateAcceptanceCriteriaNode implements AsyncNodeAction<JiraState> {

    private static final Logger log = LoggerFactory.getLogger(GenerateAcceptanceCriteriaNode.class);

    private static final String USER_REQUEST = "user_request";
    private static final String USER_STORY = "user_story";
    private static final String FORMAT = "format";

    private final ChatClient chatClient;
    private final PromptTemplate acceptanceCriteriaPromptTemplate;

    public GenerateAcceptanceCriteriaNode(
            @Qualifier(USER_STORY_CHAT_CLIENT) ChatClient chatClient,
            @Value("classpath:prompt/jira/user_story_acceptance_criteria_prompt.st") Resource userStoryAcceptanceCriteriaPrompt) {
        this.chatClient = chatClient;
        this.acceptanceCriteriaPromptTemplate = new PromptTemplate(userStoryAcceptanceCriteriaPrompt);
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(JiraState state) {
        try {
            String userMessage = state.userMessage().orElseThrow(() ->
                    new IllegalStateException("userMessage is required"));
            String storySummary = state.storySummary().orElseThrow(() ->
                    new IllegalStateException("storySummary is required"));

            log.info("Generating acceptance criteria");

            ListOutputConverter listConverter = new ListOutputConverter(new DefaultConversionService());

            Map<String, Object> templateInputs = Map.of(
                    USER_REQUEST, userMessage,
                    USER_STORY, storySummary,
                    FORMAT, listConverter.getFormat());

            Prompt acceptanceCriteriaPrompt = acceptanceCriteriaPromptTemplate.create(templateInputs);

            List<String> acceptanceCriteria = chatClient.prompt(acceptanceCriteriaPrompt)
                    .call()
                    .entity(listConverter);

            assert acceptanceCriteria != null;
            return completedFuture(Map.of(JiraState.ACCEPTANCE_CRITERIA, acceptanceCriteria));
        } catch (Exception exception) {
            log.error("Failed to generate acceptance criteria", exception);
            return failedFuture(exception);
        }
    }
}
