package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import com.solesonic.mcp.exception.ToolFailures;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.agent.config.JiraChatClientConfig.USER_STORY_CHAT_CLIENT;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class GenerateAcceptanceCriteriaNode implements AsyncNodeAction<JiraState> {

    private static final Logger log = LoggerFactory.getLogger(GenerateAcceptanceCriteriaNode.class);

    private static final String OPERATION = "Generating acceptance criteria";

    private static final String USER_REQUEST = "user_request";
    private static final String USER_STORY = "user_story";
    private static final String FORMAT = "format";

    private static final String FORMAT_INSTRUCTIONS = """
            Respond with each acceptance criterion on its own line, without any leading or trailing text,
            numbering, or bullet points.
            """;

    private static final int ACCEPTANCE_CRITERIA_MAX_TOKENS = 400;

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
            String detailedDescription = state.detailedDescription().orElseThrow(() ->
                    new IllegalStateException("detailedDescription is required"));

            log.info("Generating acceptance criteria");

            Map<String, Object> templateInputs = Map.of(
                    USER_REQUEST, userMessage,
                    USER_STORY, detailedDescription,
                    FORMAT, FORMAT_INSTRUCTIONS);

            Prompt acceptanceCriteriaPrompt = acceptanceCriteriaPromptTemplate.create(templateInputs);

            String content = chatClient.prompt(acceptanceCriteriaPrompt)
                    .options(OpenAiChatOptions.builder().maxTokens(ACCEPTANCE_CRITERIA_MAX_TOKENS))
                    .call()
                    .content();

            assert content != null;
            List<String> acceptanceCriteria = Arrays.stream(content.split("\n"))
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .toList();

            return completedFuture(Map.of(JiraState.ACCEPTANCE_CRITERIA, acceptanceCriteria));
        } catch (Exception exception) {
            log.error("Failed to generate acceptance criteria", exception);
            return failedFuture(ToolFailures.describe(OPERATION, exception));
        }
    }
}
