package com.solesonic.agent.sports.node;

import com.solesonic.agent.sports.SportsIntentClassifier;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.model.SportsEntityExtraction;
import com.solesonic.agent.sports.model.SportsQueryIntent;
import com.solesonic.agent.sports.model.SportsQuestionType;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.agent.config.SportsChatClientConfig.SPORTS_INTENT_CLIENT;
import static com.solesonic.mcp.prompt.PromptConstants.TODAY_DATE;
import static com.solesonic.mcp.prompt.PromptConstants.USER_MESSAGE;
import static com.solesonic.mcp.prompt.PromptConstants.todayDate;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
public class ParseSportsIntentNode implements AsyncNodeAction<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(ParseSportsIntentNode.class);

    @Value("classpath:prompt/sports/sports-entity-prompt.st")
    private Resource entityPromptResource;

    private final ChatClient chatClient;
    private final SportsIntentClassifier sportsIntentClassifier;

    public ParseSportsIntentNode(
            @Qualifier(SPORTS_INTENT_CLIENT) ChatClient chatClient,
            SportsIntentClassifier sportsIntentClassifier) {
        this.chatClient = chatClient;
        this.sportsIntentClassifier = sportsIntentClassifier;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state) {
        try {
            Optional<String> conversationId = state.conversationId();
            String userMessage = state.userMessage().orElseThrow();
            String currentDateTime = todayDate();

            List<SportsQuestionType> keywordQuestionTypes = sportsIntentClassifier.classify(userMessage);
            log.info("Keyword-classified intent: {}", keywordQuestionTypes);

            Prompt entityPrompt = new PromptTemplate(entityPromptResource).create(Map.of(
                    USER_MESSAGE, userMessage,
                    TODAY_DATE, currentDateTime
            ));

            SportsEntityExtraction extraction = chatClient.prompt(entityPrompt)
                    .advisors(advisorSpec -> conversationId.ifPresent(id -> advisorSpec.param(CONVERSATION_ID, id)))
                    .call()
                    .entity(SportsEntityExtraction.class);

            List<String> teams = extraction != null && extraction.teams() != null ? extraction.teams() : List.of();
            List<String> players = extraction != null && extraction.players() != null ? extraction.players() : List.of();
            String timeContext = extraction != null && extraction.timeContext() != null ? extraction.timeContext() : "upcoming";
            List<SportsQuestionType> llmQuestionTypes = extraction != null
                    && extraction.questionTypes() != null
                    && !extraction.questionTypes().isEmpty()
                    ? extraction.questionTypes()
                    : List.of();

            List<SportsQuestionType> resolvedQuestionTypes = llmQuestionTypes.isEmpty() ? keywordQuestionTypes : llmQuestionTypes;

            if (!llmQuestionTypes.isEmpty() && !llmQuestionTypes.equals(keywordQuestionTypes)) {
                log.info("LLM overrode keyword intent: {} -> {}", keywordQuestionTypes, llmQuestionTypes);
            }

            SportsQueryIntent sportsQueryIntent = new SportsQueryIntent(resolvedQuestionTypes, teams, players, timeContext);

            log.info("Parsed NBA intent: questionTypes={}, teams={}, players={}",
                    sportsQueryIntent.questionTypes(), sportsQueryIntent.teams(), sportsQueryIntent.players());

            return completedFuture(Map.of(
                    SportsState.SPORTS_QUERY_INTENT, sportsQueryIntent,
                    SportsState.CURRENT_DATE_TIME, currentDateTime
            ));
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }
}
