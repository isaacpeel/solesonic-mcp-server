package com.solesonic.mcp.tool.atlassian;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.model.atlassian.jira.User;
import com.solesonic.mcp.service.atlassian.JiraUserService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
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
    public record AssigneeRequest(@McpToolParam(description = "The name of a jira user.") String assignee) {}

    /**
     * Looks up the Jira account ID for a given assignee name/query.
     *
     * @param assigneeRequest The request used to look up an assignee for jira issues
     * @return AssigneeIdLookupResponse with the account ID or null if not found
     */
    @SuppressWarnings("unused")
    @McpTool(name = ASSIGN_JIRA, description = "Searches for a valid jira user that can be an assignee for a new jira issue.  This can be used for a general jira user search by name.")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-ASSIGNEE-LOOKUP')")
    public AssigneeResponse assigneeLookup(@McpToolParam(description = "Assignee to look up.  This is a jira user") AssigneeRequest assigneeRequest) {
        log.debug("Invoking user search for: {}", assigneeRequest);

        if(assigneeRequest == null || StringUtils.isEmpty(assigneeRequest.assignee)) {
            log.warn("Empty assignee name provided");
            throw new JiraException("Empty assignee name provided");
        }

        User user = jiraUserService.search(assigneeRequest.assignee())
                .stream()
                .findFirst()
                .orElseThrow(() -> new JiraException("No assignable user found"));

        log.debug("Found assignee with ID: {}", user.accountId());

        return new AssigneeResponse(user.accountId());
    }
}
