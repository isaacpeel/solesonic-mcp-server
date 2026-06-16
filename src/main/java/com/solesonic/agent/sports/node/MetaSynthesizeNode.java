package com.solesonic.agent.sports.node;

import com.solesonic.agent.sports.SportsState;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.solesonic.agent.config.SportsChatClientConfig.SPORTS_CHAT_CLIENT;
import static com.solesonic.mcp.prompt.PromptConstants.TODAY_DATE;
import static com.solesonic.mcp.prompt.PromptConstants.USER_MESSAGE;
import static com.solesonic.mcp.prompt.PromptConstants.todayDate;

@Component
public class MetaSynthesizeNode implements AsyncNodeActionWithConfig<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(MetaSynthesizeNode.class);

    private static final String SUB_GRAPH_RESULTS_VAR = "subGraphResults";

    private final ChatClient chatClient;

    @Value("classpath:prompt/sports/meta-synthesize.st")
    private Resource promptResource;

    public MetaSynthesizeNode(@Qualifier(SPORTS_CHAT_CLIENT) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state, RunnableConfig config) {
        SynthesisOutputEmitter emitter = config
                .metadata(SynthesisOutputEmitter.CONFIG_KEY)
                .map(SynthesisOutputEmitter.class::cast)
                .orElseGet(SynthesisOutputEmitter::noOp);

        Map<String, String> subGraphResults = state.subGraphResults().orElseThrow(
                () -> new IllegalStateException("MetaSynthesizeNode requires SUB_GRAPH_RESULTS in state"));

        String formattedResults = formatSubGraphResults(subGraphResults);
        String artifactId = UUID.randomUUID().toString();
        StringBuilder accumulated = new StringBuilder();
        AtomicReference<String> pendingToken = new AtomicReference<>(null);
        AtomicBoolean firstChunkSent = new AtomicBoolean(false);

        String promptTemplate = readResource(promptResource);
        Map<String, Object> variables = Map.of(
                USER_MESSAGE, state.userMessage().orElseThrow(),
                TODAY_DATE, todayDate(),
                SUB_GRAPH_RESULTS_VAR, formattedResults
        );
        Prompt metaPrompt = new PromptTemplate(promptTemplate).create(variables);

        log.info("Meta-synthesizing {} sub-graph results", subGraphResults.size());

        return chatClient.prompt(metaPrompt)
                .stream()
                .content()
                .doOnNext(token -> {
                    accumulated.append(token);
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
                    log.info("Meta-synthesis complete");
                })
                .then(Mono.fromCallable(() -> Map.<String, Object>of(SportsState.FINAL_ANALYSIS, accumulated.toString())))
                .toFuture();
    }

    private String formatSubGraphResults(Map<String, String> subGraphResults) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : subGraphResults.entrySet()) {
            builder.append("=== ").append(entry.getKey()).append(" Analysis ===\n");
            builder.append(entry.getValue()).append("\n\n");
        }
        return builder.toString();
    }

    private String readResource(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            throw new RuntimeException("Failed to read prompt resource: " + resource.getDescription(), ioException);
        }
    }
}
