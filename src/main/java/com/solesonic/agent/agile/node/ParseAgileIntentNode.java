package com.solesonic.agent.agile.node;

import com.solesonic.agent.agile.AgileQueryResult;
import com.solesonic.agent.agile.AgileState;
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

import static com.solesonic.agent.agile.AgileChatClientConfig.AGILE_CHAT_CLIENT;
import static com.solesonic.mcp.prompt.PromptConstants.USER_MESSAGE;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class ParseAgileIntentNode implements AsyncNodeAction<AgileState> {

    private static final Logger log = LoggerFactory.getLogger(ParseAgileIntentNode.class);

    @Value("classpath:/prompt/agile/jira_agile_prompt.st")
    private Resource jiraAgilePrompt;

    private final ChatClient chatClient;

    public ParseAgileIntentNode(@Qualifier(AGILE_CHAT_CLIENT) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(AgileState state) {
        try {
            String userMessage = state.userMessage().orElseThrow();

            PromptTemplate jiraAgilePromptTemplate = PromptTemplate.builder()
                    .resource(jiraAgilePrompt)
                    .build();

            Map<String, Object> agileParams = Map.of(USER_MESSAGE, userMessage);

            Prompt agilePrompt = jiraAgilePromptTemplate.create(agileParams);

            AgileQueryResult agileQueryResult = chatClient.prompt(agilePrompt)
                    .user(userMessage)
                    .call()
                    .responseEntity(AgileQueryResult.class)
                    .getEntity();

            assert agileQueryResult != null;
            log.info("Parsed agile intent: queryType={}, jqlFilter={}, targetStatus={}", agileQueryResult.queryType(), agileQueryResult.jqlFilter(), agileQueryResult.targetStatus());

            return completedFuture(Map.of(AgileState.AGILE_QUERY_RESULT, agileQueryResult));
        } catch (Exception exception) {
            log.error("Failed to parse agile intent", exception);
            return failedFuture(exception);
        }
    }

}
