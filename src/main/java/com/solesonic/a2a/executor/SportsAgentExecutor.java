package com.solesonic.a2a.executor;

import com.solesonic.agent.sports.SportsResearchGraphConfig;
import com.solesonic.agent.sports.SportsState;
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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Component("nba")
public class SportsAgentExecutor implements AgentExecutor {
    private static final Logger log = LoggerFactory.getLogger(SportsAgentExecutor.class);

    public static final String PROGRESS_CALLBACK_KEY = "progressCallback";

    private static final String FALLBACK_ANALYSIS = "Unable to find information for your NBA question. Please try rephrasing or check NBA.com directly.";

    private static final Map<String, String> NODE_PROGRESS_MESSAGES = Map.of(
            SportsResearchGraphConfig.PARSE_SPORTS_INTENT,          "Understanding your question...",
            SportsResearchGraphConfig.RESOLVE_ESPN_TEAM_URLS,       "Looking up team information...",
            SportsResearchGraphConfig.FETCH_ESPN_ROSTER,            "Fetching team rosters...",
            SportsResearchGraphConfig.FETCH_ESPN_STANDINGS,         "Fetching current standings...",
            SportsResearchGraphConfig.SEARCH_SCHEDULE,              "Fetching schedule from ESPN...",
            SportsResearchGraphConfig.EXTRACT_TEAMS_FROM_SCHEDULE,  "Identifying teams from the schedule...",
            SportsResearchGraphConfig.PARALLEL_SEARCH,              "Searching for current data...",
            SportsResearchGraphConfig.SEARCH_NEWS_AND_STATS,        "Searching for news and statistics..."
            // SYNTHESIZE_ANALYSIS omitted — the node streams tokens directly via progressCallback
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
    public void execute(RequestContext requestContext, EventQueue eventQueue) throws JSONRPCError {
        TaskUpdater taskUpdater = new TaskUpdater(requestContext, eventQueue);

        if (requestContext.getTask() == null) {
            taskUpdater.submit();
        }

        taskUpdater.startWork();

        String userMessage = extractText(requestContext);
        String conversationId = requestContext.getContextId();
        log.info("NBA research agent invoked: userMessage={}, conversationId={}", userMessage, conversationId);

        seedMemoryFromReferencedTasks(requestContext, conversationId);

        Consumer<String> progressCallback = message -> {
            log.debug("Progress callback with message: {}", message);

            List<Part<?>> messageParts = List.of(new TextPart(message));

            Message statusMessage = taskUpdater.newAgentMessage(messageParts, null);
            taskUpdater.updateStatus(TaskState.WORKING, statusMessage);
        };

        Map<String, Object> input = Map.of(
                SportsState.USER_MESSAGE, userMessage,
                SportsState.CONVERSATION_ID, conversationId
        );

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addMetadata(PROGRESS_CALLBACK_KEY, progressCallback)
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

                            List<Part<?>> messageParts = List.of(new TextPart(progressMessage));
                            Message statusMessage = taskUpdater.newAgentMessage(messageParts, null);

                            taskUpdater.updateStatus(TaskState.WORKING, statusMessage);
                    })
                    .join();

            SportsState finalState = finalStateRef.get();
            String analysis = finalState != null ? finalState.finalAnalysis().orElse(FALLBACK_ANALYSIS) : FALLBACK_ANALYSIS;

            taskUpdater.addArtifact(List.of(new TextPart(analysis)), null, null, null);
            taskUpdater.complete();
        } catch (Exception exception) {
            log.error("NBA research agent failed: userMessage={}", userMessage, exception);
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