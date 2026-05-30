package com.solesonic.agent.sports.node;

import com.solesonic.a2a.executor.SportsAgentExecutor;
import com.solesonic.agent.sports.SportsState;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.solesonic.agent.sports.SportsChatClientConfig.SPORTS_CHAT_CLIENT;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
public class SynthesizeSportsAnalysisNode implements AsyncNodeActionWithConfig<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(SynthesizeSportsAnalysisNode.class);

    private final ChatClient chatClient;
    private final SynthesisPromptAssembler synthesisPromptAssembler;

    public SynthesizeSportsAnalysisNode(
            @Qualifier(SPORTS_CHAT_CLIENT) ChatClient chatClient,
            SynthesisPromptAssembler synthesisPromptAssembler) {
        this.chatClient = chatClient;
        this.synthesisPromptAssembler = synthesisPromptAssembler;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state, RunnableConfig config) {
        @SuppressWarnings("unchecked")
        Consumer<String> progressCallback = config
                .metadata(SportsAgentExecutor.PROGRESS_CALLBACK_KEY)
                .map(object -> (Consumer<String>) object)
                .orElse(_ -> {});

        Prompt synthesisPrompt = synthesisPromptAssembler.assemble(state);
        Optional<String> conversationId = state.conversationId();
        StringBuilder accumulated = new StringBuilder();

        log.info("Synthesizing sports analysis for intent: {}",
                state.sportsQueryIntent()
                        .map(intent -> intent.questionTypes().toString())
                        .orElse("unknown"));

        return chatClient.prompt(synthesisPrompt)
                .advisors(advisorSpec -> conversationId.ifPresent(id -> advisorSpec.param(CONVERSATION_ID, id)))
                .stream()
                .content()
                .doOnNext(token -> {
                    accumulated.append(token);
                    progressCallback.accept(token);
                })
                .doOnComplete(() -> log.info("Sports analysis generated"))
                .then(Mono.fromCallable(() -> Map.<String, Object>of(SportsState.FINAL_ANALYSIS, accumulated.toString())))
                .toFuture();
    }
}
