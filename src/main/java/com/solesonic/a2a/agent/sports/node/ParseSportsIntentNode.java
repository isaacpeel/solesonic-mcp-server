package com.solesonic.a2a.agent.sports.node;

import com.solesonic.a2a.agent.sports.SportsState;
import com.solesonic.a2a.agent.sports.model.SportsQueryIntent;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.a2a.agent.sports.SportsChatClientConfig.SPORTS_INTENT_CLIENT;
import static com.solesonic.mcp.prompt.PromptConstants.*;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
public class ParseSportsIntentNode implements AsyncNodeAction<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(ParseSportsIntentNode.class);

    @Value("classpath:prompt/sports/sports-intent-prompt.st")
    private Resource sportsIntentPromptResource;

    private final ChatClient chatClient;

    public ParseSportsIntentNode(
            @Qualifier(SPORTS_INTENT_CLIENT) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state) {
        try {
            Optional<String> conversationId = state.conversationId();
            String userMessage = state.userMessage().orElseThrow();
            String currentDateTime = todayDate();

            PromptTemplate sportsIntentTemplate = new PromptTemplate(sportsIntentPromptResource);

            Map<String, Object> promptVars = Map.of(
                    USER_MESSAGE, userMessage,
                    TODAY_DATE, currentDateTime
            );

            Prompt sportsIntentPrompt = sportsIntentTemplate.create(promptVars);

            SportsQueryIntent sportsQueryIntent = chatClient.prompt(sportsIntentPrompt)
                    .advisors(advisorSpec -> conversationId.ifPresent(id -> advisorSpec.param(CONVERSATION_ID, id)))
                    .call()
                    .entity(SportsQueryIntent.class);

            assert sportsQueryIntent != null;
            log.info("Sports intent parse LLM response: {}", sportsQueryIntent.questionTypes());
            log.info("Parsed NBA intent: questionTypes={}, teams={}, players={}",
                    sportsQueryIntent.questionTypes(), sportsQueryIntent.teams(),
                    sportsQueryIntent.players());

            return completedFuture(Map.of(
                    SportsState.SPORTS_QUERY_INTENT, sportsQueryIntent,
                    SportsState.CURRENT_DATE_TIME, currentDateTime
            ));
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }
}
