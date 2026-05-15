package com.solesonic.a2a.agent;

import com.solesonic.mcp.workflow.CreateJiraWorkflow;
import com.solesonic.mcp.workflow.framework.UserInputRequiredException;
import com.solesonic.mcp.workflow.framework.WorkflowPendingInput;
import com.solesonic.mcp.workflow.framework.WorkflowQuestion;
import com.solesonic.mcp.workflow.model.JiraIssueCreatePayload;
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
import java.util.stream.Collectors;

/**
 * A2A executor for the Create Jira workflow.
 * <p>
 * USER_INPUT_REQUIRED: when the workflow cannot resolve required fields it throws
 * UserInputRequiredException. This executor translates that to TaskState.INPUT_REQUIRED and
 * formats the clarification questions for the A2A client. The task is left open so the client
 * can send a follow-up message. Resume from that follow-up is not yet implemented in the
 * workflow layer — the next message restarts the workflow from scratch.
 */
@Component
public class CreateJiraAgentExecutor implements AgentExecutor {
    private static final Logger log = LoggerFactory.getLogger(CreateJiraAgentExecutor.class);

    private final CreateJiraWorkflow createJiraWorkflow;
    private final JsonMapper jsonMapper;

    public CreateJiraAgentExecutor(CreateJiraWorkflow createJiraWorkflow, JsonMapper jsonMapper) {
        this.createJiraWorkflow = createJiraWorkflow;
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
        log.info("Create Jira agent invoked: userMessage={}", userMessage);

        try {
            JiraIssueCreatePayload payload =
                    createJiraWorkflow.startWorkflow(userMessage, (_, progressMessage) -> {
                        Message statusMessage = taskUpdater.newAgentMessage(
                                List.of(new TextPart(progressMessage)), null);
                        taskUpdater.updateStatus(TaskState.WORKING, statusMessage);
                    });

            String payloadJson = jsonMapper.writeValueAsString(payload);

            taskUpdater.addArtifact(List.of(new TextPart(payloadJson)), "jira-issue-payload", null, null);
            taskUpdater.complete();
        } catch (UserInputRequiredException userInputRequiredException) {
            String clarificationMessage = formatClarificationMessage(userInputRequiredException.getPendingInput());
            Message inputRequiredMessage = taskUpdater.newAgentMessage(
                    List.of(new TextPart(clarificationMessage)), null);
            taskUpdater.updateStatus(TaskState.INPUT_REQUIRED, inputRequiredMessage);
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

    private static String formatClarificationMessage(WorkflowPendingInput pendingInput) {
        if (pendingInput == null || pendingInput.questions() == null || pendingInput.questions().isEmpty()) {
            return "Additional information is required to create the Jira issue.";
        }

        String questions = pendingInput.questions().stream()
                .map(WorkflowQuestion::prompt)
                .collect(Collectors.joining("\n- ", "- ", ""));

        return "Please provide the following information to complete the Jira issue:\n" + questions;
    }
}
