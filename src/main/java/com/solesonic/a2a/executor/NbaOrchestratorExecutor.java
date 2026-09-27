package com.solesonic.a2a.executor;

import com.solesonic.a2a.config.A2ARedisConfiguration;
import com.solesonic.agent.checkpoint.GraphRunner;
import com.solesonic.agent.checkpoint.GraphThread;
import com.solesonic.agent.nba.NbaOrchestratorGraphConfig;
import com.solesonic.agent.nba.SportsState;
import com.solesonic.agent.nba.node.SynthesisOutputEmitter;
import com.solesonic.mcp.security.identity.CallerIdentity;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;
import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Component("nba")
public class NbaOrchestratorExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(NbaOrchestratorExecutor.class);

    public static final String TASK_UPDATER_KEY = "taskUpdater";

    private static final String FALLBACK_ANALYSIS =
            "Unable to find information for your NBA question. Please try rephrasing or check NBA.com directly.";

    private static final Map<String, String> NODE_PROGRESS_MESSAGES = Map.of(
            NbaOrchestratorGraphConfig.PARSE_SPORTS_INTENT, "Understanding your question...",
            NbaOrchestratorGraphConfig.FAN_OUT,             "Running specialized analyses...",
            NbaOrchestratorGraphConfig.META_SYNTHESIZE,     "Combining analyses..."
    );

    private final CompiledGraph<SportsState> nbaOrchestratorGraph;
    private final ChatMemory chatMemory;
    private final TaskStore taskStore;
    private final GraphRunner graphRunner;

    public NbaOrchestratorExecutor(
            @Qualifier("nbaOrchestratorGraph") CompiledGraph<SportsState> nbaOrchestratorGraph,
            @Qualifier(A2ARedisConfiguration.SPORTS_CHAT_MEMORY) ChatMemory chatMemory,
            TaskStore taskStore,
            GraphRunner graphRunner) {
        this.nbaOrchestratorGraph = nbaOrchestratorGraph;
        this.chatMemory = chatMemory;
        this.taskStore = taskStore;
        this.graphRunner = graphRunner;
    }

    @Override
    public void execute(@NonNull RequestContext requestContext, @NonNull AgentEmitter agentEmitter) throws A2AError {
        if (agentEmitter.getTaskId() == null) {
            agentEmitter.submit();
        }

        agentEmitter.startWork();

        assert requestContext.getMessage() != null;

        String userMessage = extractTextFromMessage(requestContext.getMessage());
        String conversationId = requestContext.getContextId();
        log.info("NBA orchestrator invoked: userMessage={}, conversationId={}", userMessage, conversationId);

        seedMemoryFromReferencedTasks(requestContext, conversationId);

        SynthesisOutputEmitter emitter = SynthesisOutputEmitter.fromTaskUpdater(agentEmitter);

        // The NBA graph calls no per-user APIs, so it runs without a caller when security is disabled.
        Optional<CallerIdentity> callerIdentity = callerIdentity(requestContext);

        Map<String, Object> input = new HashMap<>();
        input.put(SportsState.USER_MESSAGE, userMessage);
        input.put(SportsState.CONVERSATION_ID, conversationId);
        callerIdentity.ifPresent(caller -> input.put(SportsState.CALLER_IDENTITY, caller));

        GraphThread graphThread = callerIdentity
                .map(caller -> GraphThread.run(NbaOrchestratorGraphConfig.GRAPH_NAME, caller))
                .orElseGet(() -> new GraphThread(NbaOrchestratorGraphConfig.GRAPH_NAME, UUID.randomUUID().toString()));

        RunnableConfig runnableConfig = graphThread.runnableConfigBuilder()
                .addMetadata(SynthesisOutputEmitter.CONFIG_KEY, emitter)
                .addMetadata(TASK_UPDATER_KEY, agentEmitter)
                .build();

        try {
            NodeOutput<SportsState> finalOutput = graphRunner.run(nbaOrchestratorGraph, GraphInput.args(input), runnableConfig, output -> {
                String nodeName = output.node();

                log.debug("Orchestrator node output: {}", nodeName);

                if (END.equals(nodeName) || START.equals(nodeName)) {
                    return;
                }

                String progressMessage = NODE_PROGRESS_MESSAGES.get(nodeName);

                if (progressMessage == null) {
                    return;
                }

                emitter.emitProgress(progressMessage);
            });

            SportsState finalState = finalOutput == null ? null : finalOutput.state();

            if (finalState != null && finalState.finalAnalysis().isPresent()) {
                String finalAnalysis = finalState.finalAnalysis().get();

                if (StringUtils.isNoneBlank(conversationId)) {
                    chatMemory.add(conversationId, List.of(
                            new UserMessage(userMessage),
                            new AssistantMessage(finalAnalysis)
                    ));
                }

                agentEmitter.complete();
            } else {
                agentEmitter.addArtifact(List.of(new TextPart(FALLBACK_ANALYSIS)), null, null, null);
                agentEmitter.complete();
            }
        } catch (Exception exception) {
            log.error("NBA orchestrator failed: userMessage={}", userMessage, exception);
            agentEmitter.fail();
        }
    }

    @Override
    public void cancel(@NonNull RequestContext context, AgentEmitter agentEmitter) throws A2AError {
        agentEmitter.cancel();
    }

    private static Optional<CallerIdentity> callerIdentity(RequestContext requestContext) {
        ServerCallContext callContext = requestContext.getCallContext();

        if (callContext == null || !callContext.getUser().isAuthenticated()) {
            return Optional.empty();
        }

        try {
            return Optional.of(CallerIdentity.of(callContext.getUser().getUsername()));
        } catch (IllegalArgumentException invalidSubject) {
            log.warn("A2A caller subject is not a user id; running the NBA graph without a caller identity", invalidSubject);
            return Optional.empty();
        }
    }

    public static String extractTextFromMessage(Message message) {
        StringBuilder textBuilder = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                textBuilder.append(textPart.text());
            }
        }
        return textBuilder.toString();
    }

    private void seedMemoryFromReferencedTasks(RequestContext requestContext, String conversationId) {
        assert requestContext.getMessage() != null;

        List<String> referenceTaskIds = requestContext.getMessage().referenceTaskIds();
        if (referenceTaskIds == null || referenceTaskIds.isEmpty()) {
            return;
        }

        if (!chatMemory.get(conversationId).isEmpty()) {
            return;
        }

        for (String referencedTaskId : referenceTaskIds) {
            Task referencedTask = taskStore.get(referencedTaskId);
            if (referencedTask == null) {
                continue;
            }

            String priorUserQuestion = referencedTask.history() == null ? null :
                    referencedTask.history().stream()
                            .filter(historyMessage -> historyMessage.role() == Message.Role.ROLE_USER)
                            .flatMap(historyMessage -> historyMessage.parts().stream())
                            .filter(part -> part instanceof TextPart)
                            .map(part -> ((TextPart) part).text())
                            .findFirst()
                            .orElse(null);

            if (priorUserQuestion == null) {
                continue;
            }

            assert referencedTask.artifacts() != null;

            referencedTask.artifacts().stream()
                    .flatMap(artifact -> artifact.parts().stream())
                    .filter(part -> part instanceof TextPart)
                    .map(part -> ((TextPart) part).text())
                    .findFirst()
                    .ifPresent(artifactText -> chatMemory.add(conversationId, List.of(
                            new UserMessage(priorUserQuestion),
                            new AssistantMessage(artifactText)
                    )));
        }
    }
}
