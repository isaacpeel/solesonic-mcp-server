package com.solesonic.a2a.executor;

import com.solesonic.agent.sports.NbaAgentGraphConfig;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.node.SynthesisOutputEmitter;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.*;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@SuppressWarnings("unused")
public class SportsAgentExecutor implements AgentExecutor {
    private static final Logger log = LoggerFactory.getLogger(SportsAgentExecutor.class);

    public static final String TASK_UPDATER_KEY = "taskUpdater";

    private static final String FALLBACK_ANALYSIS = "Unable to find information for your NBA question. Please try rephrasing or check NBA.com directly.";

    private static final Map<String, String> NODE_PROGRESS_MESSAGES = Map.of(
            NbaAgentGraphConfig.PARSE_SPORTS_INTENT, "Understanding your question...",
            NbaAgentGraphConfig.RESOLVE_ESPN_TEAM_URLS, "Looking up team information...",
            NbaAgentGraphConfig.FETCH_ESPN_ROSTER, "Fetching team rosters...",
            NbaAgentGraphConfig.FETCH_ESPN_STANDINGS, "Fetching current standings...",
            NbaAgentGraphConfig.SEARCH_SCHEDULE, "Fetching schedule from ESPN...",
            NbaAgentGraphConfig.EXTRACT_TEAMS_FROM_SCHEDULE, "Identifying teams from the schedule...",
            NbaAgentGraphConfig.SEARCH_NEWS, "Searching for current news...",
            NbaAgentGraphConfig.SEARCH_STATS, "Searching for statistics..."
            // SYNTHESIZE_ANALYSIS omitted — the node streams tokens as artifact chunks directly
    );

    private final CompiledGraph<SportsState> sportsResearchGraph;
    private final ChatMemory chatMemory;
    private final TaskStore taskStore;

    public SportsAgentExecutor(
            CompiledGraph<SportsState> sportsResearchGraph,
            ChatMemory chatMemory,
            TaskStore taskStore) {
        this.sportsResearchGraph = sportsResearchGraph;
        this.chatMemory = chatMemory;
        this.taskStore = taskStore;
    }

    @Override
    public void execute(@NonNull RequestContext requestContext, @NonNull AgentEmitter agentEmitter) throws A2AError {
        agentEmitter.startWork();

        assert requestContext.getMessage() != null;

        String userMessage = extractTextFromMessage(requestContext.getMessage());
        String conversationId = requestContext.getContextId();
        log.info("NBA research agent invoked: userMessage={}, conversationId={}", userMessage, conversationId);

        seedMemoryFromReferencedTasks(requestContext, conversationId);

        Map<String, Object> input = Map.of(
                SportsState.USER_MESSAGE, userMessage,
                SportsState.CONVERSATION_ID, conversationId
        );

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addMetadata(SynthesisOutputEmitter.CONFIG_KEY, agentEmitter)
                .addMetadata(TASK_UPDATER_KEY, agentEmitter)
                .build();

        AtomicReference<SportsState> finalStateRef = new AtomicReference<>();

        try {
            sportsResearchGraph.stream(input, runnableConfig)
                    .forEachAsync(output -> {
                        finalStateRef.set(output.state());

                        String nodeName = output.node();

                        log.debug("Processing output: {}", nodeName);

                        if (END.equals(nodeName) || START.equals(nodeName)) {
                            return;
                        }

                        String progressMessage = NODE_PROGRESS_MESSAGES.getOrDefault(nodeName, null);

                        if (progressMessage == null) {
                            return;
                        }

                        agentEmitter.sendMessage(progressMessage);
                    })
                    .join();

            SportsState finalState = finalStateRef.get();

            if (finalState != null && finalState.finalAnalysis().isPresent()) {
                // Synthesis node streamed the artifact directly as chunks — just complete
                agentEmitter.complete();
            } else {
                agentEmitter.addArtifact(List.of(new TextPart(FALLBACK_ANALYSIS)), null, null, null);
                agentEmitter.complete();
            }
        } catch (Exception exception) {
            log.error("NBA research agent failed: userMessage={}", userMessage, exception);
            agentEmitter.fail();
        }
    }

    @Override
    public void cancel(@NonNull RequestContext context, @NonNull AgentEmitter agentEmitter) throws A2AError {
        agentEmitter.cancel();
    }

    private void seedMemoryFromReferencedTasks(RequestContext requestContext, String conversationId) {
        List<String> referenceTaskIds = Objects.requireNonNull(requestContext.getMessage()).referenceTaskIds();
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

    public static String extractTextFromMessage(Message message) {
        StringBuilder textBuilder = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                textBuilder.append(textPart.text());
            }
        }
        return textBuilder.toString();
    }
}
