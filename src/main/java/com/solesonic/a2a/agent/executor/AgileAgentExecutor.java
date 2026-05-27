package com.solesonic.a2a.agent.executor;

import com.solesonic.a2a.agent.agile.AgileQueryResult;
import com.solesonic.a2a.agent.agile.AgileState;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskStore;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Component("agile-query-agent")
public class AgileAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgileAgentExecutor.class);

    private static final String FALLBACK_RESULT = "{}";

    private final CompiledGraph<AgileState> agileResearchGraph;
    private final ChatMemory chatMemory;
    private final TaskStore taskStore;
    private final JsonMapper jsonMapper;

    public AgileAgentExecutor(
            CompiledGraph<AgileState> agileResearchGraph,
            ChatMemory chatMemory,
            TaskStore taskStore,
            JsonMapper jsonMapper) {
        this.agileResearchGraph = agileResearchGraph;
        this.chatMemory = chatMemory;
        this.taskStore = taskStore;
        this.jsonMapper = jsonMapper;
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
        log.info("Agile query agent invoked: userMessage={}, conversationId={}", userMessage, conversationId);

        seedMemoryFromReferencedTasks(requestContext, conversationId);

        Map<String, Object> input = Map.of(
                AgileState.USER_MESSAGE, userMessage,
                AgileState.CONVERSATION_ID, conversationId
        );

        RunnableConfig runnableConfig = RunnableConfig.builder().build();

        AtomicReference<AgileState> finalStateRef = new AtomicReference<>();

        try {
            agileResearchGraph.stream(input, runnableConfig)
                    .forEachAsync(output -> {
                        finalStateRef.set(output.state());

                        String node = output.node();
                        log.debug("Processing output: {}", node);

                        switch (node) {
                            case START -> node = "Started: agile query agent.";
                            case END -> node = "Agile query agent completed.";
                            default -> node = "Completed: " + node;
                        }

                        List<io.a2a.spec.Part<?>> messageParts = List.of(new TextPart(node));
                        Message statusMessage = taskUpdater.newAgentMessage(messageParts, null);
                        taskUpdater.updateStatus(TaskState.WORKING, statusMessage);
                    })
                    .join();

            AgileState finalState = finalStateRef.get();
            AgileQueryResult agileQueryResult = finalState != null
                    ? finalState.agileQueryResult().orElse(null)
                    : null;

            String resultJson = agileQueryResult != null
                    ? jsonMapper.writeValueAsString(agileQueryResult)
                    : FALLBACK_RESULT;

            taskUpdater.addArtifact(List.of(new TextPart(resultJson)), "agile-query-result", null, null);
            taskUpdater.complete();
        } catch (Exception exception) {
            log.error("Agile query agent failed: userMessage={}", userMessage, exception);
            taskUpdater.fail();
        }
    }

    @Override
    public void cancel(RequestContext requestContext, EventQueue eventQueue) throws JSONRPCError {
        TaskUpdater taskUpdater = new TaskUpdater(requestContext, eventQueue);
        taskUpdater.cancel();
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
