package com.solesonic.agent.jira.node;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.mcp.service.atlassian.AssigneeResolutionService;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class ResolveAssigneeNode implements AsyncNodeAction<JiraState> {

    private static final Logger log = LoggerFactory.getLogger(ResolveAssigneeNode.class);

    private final AssigneeResolutionService assigneeResolutionService;

    public ResolveAssigneeNode(AssigneeResolutionService assigneeResolutionService) {
        this.assigneeResolutionService = assigneeResolutionService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(JiraState state) {
        try {
            String userMessage = state.userMessage().orElseThrow(() ->
                    new IllegalStateException("userMessage is required"));

            log.info("Resolving assignee for: {}", userMessage);

            AssigneeLookupResult assigneeLookupResult = assigneeResolutionService.resolve(userMessage);

            Map<String, Object> updates = new HashMap<>();
            updates.put(JiraState.ASSIGNEE_LOOKUP_RESULT, assigneeLookupResult);
            updates.put(JiraState.ASSIGNEE_NOT_RESOLVED, false);
            return completedFuture(updates);
        } catch (JiraException jiraException) {
            log.warn("Assignee could not be resolved: {}", jiraException.getMessage());
            Map<String, Object> updates = new HashMap<>();
            updates.put(JiraState.ASSIGNEE_NOT_RESOLVED, true);
            return completedFuture(updates);
        } catch (Exception exception) {
            log.error("Failed to resolve assignee", exception);
            return failedFuture(exception);
        }
    }
}
