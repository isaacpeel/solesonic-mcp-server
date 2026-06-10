package com.solesonic.agent.sports.node;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.solesonic.agent.config.SportsChatClientConfig.SPORTS_CHAT_CLIENT;

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
        SynthesisOutputEmitter emitter = config
                .metadata(SynthesisOutputEmitter.CONFIG_KEY)
                .map(SynthesisOutputEmitter.class::cast)
                .orElseGet(SynthesisOutputEmitter::noOp);

        Prompt synthesisPrompt = synthesisPromptAssembler.assemble(state);
        String artifactId = UUID.randomUUID().toString();
        StringBuilder accumulated = new StringBuilder();
        AtomicReference<String> pendingToken = new AtomicReference<>(null);
        AtomicBoolean firstChunkSent = new AtomicBoolean(false);

        log.info("Synthesizing sports analysis for intent: {}",
                state.sportsQueryIntent()
                        .map(intent -> intent.questionTypes().toString())
                        .orElse("unknown"));

        return chatClient.prompt(synthesisPrompt)
                .stream()
                .content()
                .doOnNext(token -> {
                    accumulated.append(token);
                    // Buffer one token ahead so the final token can be sent with lastChunk=true
                    String previous = pendingToken.getAndSet(token);
                    if (previous != null) {
                        boolean isFirst = !firstChunkSent.getAndSet(true);
                        emitter.emitChunk(previous, artifactId, isFirst ? null : true, false);
                    }
                })
                .doOnComplete(() -> {
                    String lastToken = pendingToken.get();
                    boolean isFirst = !firstChunkSent.get();
                    emitter.emitChunk(lastToken != null ? lastToken : "", artifactId, isFirst ? null : true, true);
                    log.info("Sports analysis generated");
                })
                .then(Mono.fromCallable(() -> Map.<String, Object>of(SportsState.FINAL_ANALYSIS, accumulated.toString())))
                .toFuture();
    }
}
