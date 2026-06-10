package com.solesonic.a2a.executor;

import com.solesonic.agent.sports.NbaOrchestratorGraphConfig;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.node.SynthesisOutputEmitter;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskStore;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.*;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    public NbaOrchestratorExecutor(
            @Qualifier("nbaOrchestratorGraph") CompiledGraph<SportsState> nbaOrchestratorGraph,
            ChatMemory chatMemory,
            TaskStore taskStore) {
        this.nbaOrchestratorGraph = nbaOrchestratorGraph;
        this.chatMemory = chatMemory;
        this.taskStore = taskStore;
    }

    @Override
    public void execute(RequestContext requestContext, EventQueue eventQueue) throws JSONRPCError {
        TaskUpdater taskUpdater = new TaskUpdater(requestContext, eventQueue);

        if (requestContext.getTask() == null) {
            taskUpdater.submit();
        }

        taskUpdater.startWork();

        String userMessage = extractText(requestContext);
        String conversationId = requestContext.getContextId();
        log.info("NBA orchestrator invoked: userMessage={}, conversationId={}", userMessage, conversationId);

        seedMemoryFromReferencedTasks(requestContext, conversationId);

        SynthesisOutputEmitter emitter = SynthesisOutputEmitter.fromTaskUpdater(taskUpdater);

        Map<String, Object> input = Map.of(
                SportsState.USER_MESSAGE, userMessage,
                SportsState.CONVERSATION_ID, conversationId
        );

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addMetadata(SynthesisOutputEmitter.CONFIG_KEY, emitter)
                .addMetadata(TASK_UPDATER_KEY, taskUpdater)
                .build();

        AtomicReference<SportsState> finalStateRef = new AtomicReference<>();

        try {
            nbaOrchestratorGraph.stream(input, runnableConfig)
                    .forEachAsync(output -> {
                        finalStateRef.set(output.state());

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
                    })
                    .join();

            SportsState finalState = finalStateRef.get();

            if (finalState != null && finalState.finalAnalysis().isPresent()) {
                String finalAnalysis = finalState.finalAnalysis().get();
                if (conversationId != null && !conversationId.isBlank()) {
                    chatMemory.add(conversationId, List.of(
                            new UserMessage(userMessage),
                            new AssistantMessage(finalAnalysis)
                    ));
                }
                taskUpdater.complete();
            } else {
                taskUpdater.addArtifact(List.of(new TextPart(FALLBACK_ANALYSIS)), null, null, null);
                taskUpdater.complete();
            }
        } catch (Exception exception) {
            log.error("NBA orchestrator failed: userMessage={}", userMessage, exception);
            taskUpdater.fail();
        }
    }

    @Override
    public void cancel(RequestContext context, EventQueue queue) throws JSONRPCError {
        TaskUpdater updater = new TaskUpdater(context, queue);
        updater.cancel();
    }

    private void seedMemoryFromReferencedTasks(RequestContext requestContext, String conversationId) {
        List<String> referenceTaskIds = requestContext.getMessage().getReferenceTaskIds();
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

            String priorUserQuestion = referencedTask.getHistory() == null ? null :
                    referencedTask.getHistory().stream()
                            .filter(historyMessage -> historyMessage.getRole() == Message.Role.USER)
                            .flatMap(historyMessage -> historyMessage.getParts().stream())
                            .filter(part -> part instanceof TextPart)
                            .map(part -> ((TextPart) part).getText())
                            .findFirst()
                            .orElse(null);

            if (priorUserQuestion == null) {
                continue;
            }

            referencedTask.getArtifacts().stream()
                    .flatMap(artifact -> artifact.parts().stream())
                    .filter(part -> part instanceof TextPart)
                    .map(part -> ((TextPart) part).getText())
                    .findFirst()
                    .ifPresent(artifactText -> chatMemory.add(conversationId, List.of(
                            new UserMessage(priorUserQuestion),
                            new AssistantMessage(artifactText)
                    )));
        }
    }

    private static String extractText(RequestContext requestContext) {
        return requestContext.getMessage()
                .getParts()
                .stream()
                .filter(part -> part instanceof TextPart)
                .map(part -> ((TextPart) part).getText())
                .findFirst()
                .orElse("");
    }
}
