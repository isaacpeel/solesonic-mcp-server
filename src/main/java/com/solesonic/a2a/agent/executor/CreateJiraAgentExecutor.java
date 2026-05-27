package com.solesonic.a2a.agent.executor;

import com.solesonic.a2a.agent.jira.JiraState;
import com.solesonic.a2a.agent.model.JiraIssueCreatePayload;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Component("create-jira-agent")
public class CreateJiraAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(CreateJiraAgentExecutor.class);

    private static final String ASSIGNEE_REQUIRED_MESSAGE =
            "Please provide the name of the person to assign this Jira issue to.";

    private final CompiledGraph<JiraState> createJiraGraph;
    private final JsonMapper jsonMapper;

    public CreateJiraAgentExecutor(CompiledGraph<JiraState> createJiraGraph, JsonMapper jsonMapper) {
        this.createJiraGraph = createJiraGraph;
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
        log.info("Create Jira agent invoked: userMessage={}, conversationId={}", userMessage, conversationId);

        Map<String, Object> input = Map.of(
                JiraState.USER_MESSAGE, userMessage,
                JiraState.CONVERSATION_ID, conversationId
        );

        RunnableConfig runnableConfig = RunnableConfig.builder().build();
        AtomicReference<JiraState> finalStateRef = new AtomicReference<>();

        try {
            createJiraGraph.stream(input, runnableConfig)
                    .forEachAsync(output -> {
                        finalStateRef.set(output.state());

                        String node = output.node();
                        log.debug("Processing output: {}", node);

                        switch (node) {
                            case START -> node = "Started: create Jira agent.";
                            case END -> node = "Create Jira agent completed.";
                            default -> node = "Completed: " + node;
                        }

                        List<Part<?>> messageParts = List.of(new TextPart(node));
                        Message statusMessage = taskUpdater.newAgentMessage(messageParts, null);
                        taskUpdater.updateStatus(TaskState.WORKING, statusMessage);
                    })
                    .join();

            JiraState finalState = finalStateRef.get();

            if (finalState != null && finalState.assigneeNotResolved().orElse(false)) {
                Message inputRequiredMessage = taskUpdater.newAgentMessage(
                        List.of(new TextPart(ASSIGNEE_REQUIRED_MESSAGE)), null);
                taskUpdater.updateStatus(TaskState.INPUT_REQUIRED, inputRequiredMessage);
                return;
            }

            JiraIssueCreatePayload payload = finalState != null
                    ? finalState.finalPayload().orElse(null)
                    : null;

            String payloadJson = payload != null
                    ? jsonMapper.writeValueAsString(payload)
                    : "{}";

            taskUpdater.addArtifact(List.of(new TextPart(payloadJson)), "jira-issue-payload", null, null);
            taskUpdater.complete();
        } catch (Exception exception) {
            log.error("Create Jira agent failed: userMessage={}", userMessage, exception);
            taskUpdater.fail();
        }
    }

    @Override
    public void cancel(RequestContext requestContext, EventQueue eventQueue) throws JSONRPCError {
        TaskUpdater taskUpdater = new TaskUpdater(requestContext, eventQueue);
        taskUpdater.cancel();
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
