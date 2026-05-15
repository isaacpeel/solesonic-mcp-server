package com.solesonic.a2a.agent;

import com.solesonic.mcp.workflow.AgileQueryWorkflow;
import com.solesonic.mcp.workflow.agile.AgileQueryResult;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowContext;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
public class AgileAgentExecutor implements AgentExecutor {
    private static final Logger log = LoggerFactory.getLogger(AgileAgentExecutor.class);

    private final AgileQueryWorkflow agileQueryWorkflow;
    private final JsonMapper jsonMapper;

    public AgileAgentExecutor(AgileQueryWorkflow agileQueryWorkflow, JsonMapper jsonMapper) {
        this.agileQueryWorkflow = agileQueryWorkflow;
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
        log.info("Agile query agent invoked: userMessage={}", userMessage);

        try {
            AgileQueryWorkflowContext workflowContext =
                    agileQueryWorkflow.startWorkflow(userMessage, (_, progressMessage) -> {
                        Message statusMessage = taskUpdater.newAgentMessage(
                                List.of(new TextPart(progressMessage)), null);
                        taskUpdater.updateStatus(TaskState.WORKING, statusMessage);
                    });

            AgileQueryResult agileQueryResult = workflowContext.getAgileQueryResult();
            String resultJson = jsonMapper.writeValueAsString(agileQueryResult);

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
