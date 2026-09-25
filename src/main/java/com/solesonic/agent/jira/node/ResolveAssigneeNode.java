package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.model.AssigneeResolution;
import com.solesonic.service.atlassian.AssigneeResolutionService;
import com.solesonic.mcp.exception.ToolFailures;
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

    private static final String OPERATION = "Resolving the issue assignee";

    private final AssigneeResolutionService assigneeResolutionService;

    public ResolveAssigneeNode(AssigneeResolutionService assigneeResolutionService) {
        this.assigneeResolutionService = assigneeResolutionService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(JiraState jiraState) {
        log.info("Resolve Assignee Node: apply");

        try {
            String userMessage = jiraState.userMessage()
                    .orElseThrow(() -> new IllegalStateException("userMessage is required"));

            log.info("Resolving assignee for: {}", userMessage);

            AssigneeResolution assigneeResolution = assigneeResolutionService.resolve(userMessage);

            Map<String, Object> updates = new HashMap<>();

            switch (assigneeResolution) {
                case AssigneeResolution.Resolved resolved -> {
                    updates.put(JiraState.ASSIGNEE_LOOKUP_RESULT, resolved.assigneeLookupResult());
                    updates.put(JiraState.ASSIGNEE_NOT_RESOLVED, false);
                }
                case AssigneeResolution.Ambiguous ambiguous -> {
                    log.info("Assignee not resolved: {} users match: {}; the user will choose between them", ambiguous.candidates().size(), ambiguous.searchTerm());

                    updates.put(JiraState.ASSIGNEE_NOT_RESOLVED, true);
                    updates.put(JiraState.ASSIGNEE_CANDIDATES, ambiguous.candidates());
                }
                case AssigneeResolution.NotFound notFound -> {
                    log.info("Assignee not resolved: nobody matches:{}; the user will pick from every assignable user", notFound.searchTerm());
                    updates.put(JiraState.ASSIGNEE_NOT_RESOLVED, true);
                }
                case AssigneeResolution.NotRequested _ -> {
                    log.info("Assignee not resolved: the request names nobody; the user will pick from every assignable user");
                    updates.put(JiraState.ASSIGNEE_NOT_RESOLVED, true);
                }
            }

            return completedFuture(updates);
        } catch (Exception exception) {
            log.error("Failed to resolve assignee", exception);
            return failedFuture(ToolFailures.describe(OPERATION, exception));
        }
    }
}
