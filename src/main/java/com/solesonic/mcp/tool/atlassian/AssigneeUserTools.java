package com.solesonic.mcp.tool.atlassian;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.model.atlassian.jira.User;
import com.solesonic.mcp.service.atlassian.JiraUserService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AssigneeUserTools {
    private static final Logger log = LoggerFactory.getLogger(AssigneeUserTools.class);

    public static final String ASSIGN_JIRA = "assignee_lookup";

   private final JiraUserService jiraUserService;

    public AssigneeUserTools(JiraUserService jiraUserService) {
        this.jiraUserService = jiraUserService;
    }

    public record AssigneeResponse(String assigneeId) {}
    public record AssigneeRequest(String assignee) {}

    /**
     * Looks up the Jira account ID for a given assignee name/query.
     *
     * @param assigneeRequest The request used to look up an assignee for jira issues
     * @return AssigneeIdLookupResponse with the account ID or null if not found
     */
    @SuppressWarnings("unused")
    @McpTool(name = ASSIGN_JIRA, description = "Searches for a valid assignee for a new jira issue.")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-ASSIGNEE-LOOKUP')")
    public AssigneeResponse assigneeLookup(
            McpSyncServerExchange mcpSyncServerExchange,
            @ToolParam(description = "Assignee to look up.") AssigneeRequest assigneeRequest
        ) {
        log.debug("Invoking user search for: {}", assigneeRequest);

        if(assigneeRequest == null || StringUtils.isEmpty(assigneeRequest.assignee)) {
            log.warn("Empty assignee name provided");
            throw new JiraException("Empty assignee name provided");
        }

        mcpSyncServerExchange.loggingNotification(McpSchema.LoggingMessageNotification.builder()
                .level(McpSchema.LoggingLevel.INFO)
                .data("Invoking user search for: "+ assigneeRequest.assignee)
                .build());

        User user = jiraUserService.search(assigneeRequest.assignee())
                .stream()
                .findFirst()
                .orElseThrow(() -> new JiraException("No assignable user found"));

        log.debug("Found assignee with ID: {}", user.accountId());

        return new AssigneeResponse(user.accountId());
    }
}
