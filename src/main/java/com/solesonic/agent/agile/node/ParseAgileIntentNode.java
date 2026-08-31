package com.solesonic.agent.agile.node;

import com.solesonic.agent.agile.AgileQueryIntent;
import com.solesonic.agent.agile.AgileState;
import com.solesonic.mcp.exception.ToolFailures;
import org.bsc.langgraph4j.action.AsyncNodeAction;
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
import java.util.concurrent.CompletableFuture;

import static com.solesonic.agent.config.AgileChatClientConfig.AGILE_CHAT_CLIENT;
import static com.solesonic.mcp.prompt.PromptConstants.USER_MESSAGE;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class ParseAgileIntentNode implements AsyncNodeAction<AgileState> {

    private static final Logger log = LoggerFactory.getLogger(ParseAgileIntentNode.class);

    private static final String OPERATION = "Parsing the agile query intent";

    private final ChatClient chatClient;

    // The prompt resource is a constructor parameter rather than a @Value field so tests can
    // supply a template of their own without a test-only constructor or reflection.
    private final Resource jiraAgilePrompt;

    public ParseAgileIntentNode(@Qualifier(AGILE_CHAT_CLIENT) ChatClient chatClient,
                                @Value("classpath:/prompt/agile/jira_agile_prompt.st") Resource jiraAgilePrompt) {
        this.chatClient = chatClient;
        this.jiraAgilePrompt = jiraAgilePrompt;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(AgileState agileState) {
        try {
            String userMessage = agileState.userMessage()
                    .orElseThrow(() -> new IllegalStateException(
                            "The agile state carries no user message to parse an intent from."));

            PromptTemplate jiraAgilePromptTemplate = PromptTemplate.builder()
                    .resource(jiraAgilePrompt)
                    .build();

            Map<String, Object> agileParams = Map.of(USER_MESSAGE, userMessage);

            Prompt agilePrompt = jiraAgilePromptTemplate.create(agileParams);

            AgileQueryIntent agileQueryIntent = chatClient.prompt(agilePrompt)
                    .user(userMessage)
                    .call()
                    .responseEntity(AgileQueryIntent.class)
                    .getEntity();

            // Not an assert: assertions are disabled unless the JVM runs with -ea, which would let
            // a null intent fall through into Map.of and throw a NullPointerException carrying no
            // message at all.
            if (agileQueryIntent == null) {
                throw new IllegalStateException(
                        "The agile model returned no parseable %s for user message: %s"
                                .formatted(AgileQueryIntent.class.getSimpleName(), userMessage));
            }

            log.debug("Intent: {}", agileQueryIntent.userIntent());

            return completedFuture(Map.of(AgileState.AGILE_QUERY_INTENT, agileQueryIntent));
        } catch (Exception exception) {
            log.error("Failed to parse the agile intent using prompt resource {}", jiraAgilePrompt, exception);
            return failedFuture(ToolFailures.describe(OPERATION, exception));
        }
    }
}
