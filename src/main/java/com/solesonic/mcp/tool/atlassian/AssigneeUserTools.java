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

    public static final String ASSIGN_JIRA = "jira_assignee_search";

    public static final String TOOL_DESCRIPTION = """
            Look up a JIRA user's accountId so they can be set as the assignee on a JIRA issue.
            
            EXPECTED USAGE (for LLM tool calls):
            - Call this tool when you need to assign an issue to a specific human user.
            - You MUST provide a non-empty 'jiraUserName' inside 'assigneeRequest'.
            - 'jiraUserName' should be the user's JIRA username or a clear name search term
              (e.g. "jdoe", "Jane Doe").
            
            This tool:
            - Performs a JIRA user search using the provided jiraUserName text.
            - Returns the first matching user who is assignable to issues.
            - Throws an error if jiraUserName is empty or no assignable user is found.
            """;

    public static final String PARAM_DESCRIPTION = """
            Wrapper object containing the JIRA user search input.
            
            Fields:
            - jiraUserName: REQUIRED. Non-empty string with the target user's JIRA username
              or display name fragment.
            
            For LLMs:
            - Always construct this object with a meaningful jiraUserName.
            - Do NOT omit jiraUserName, leave it empty, or fill it with placeholders.
            - Example valid values: "jdoe", "Jane Doe", "alice.smith".
            """;

    private final JiraUserService jiraUserService;

    public AssigneeUserTools(JiraUserService jiraUserService) {
        this.jiraUserService = jiraUserService;
    }

    public record AssigneeResponse(String assigneeId) {
    }

    public record AssigneeRequest(
            @McpToolParam(
                    description = """
                            JIRA user identifier text used to search for an assignee.
                            
                            This MUST be a non-empty string and SHOULD be either:
                            - The exact JIRA username / account name, or
                            - A part of the user's display name (e.g. "Alice Smith", "asmith").
                            
                            IMPORTANT FOR LLMs:
                            - Never pass null or an empty string.
                            - Do not send generic values like "user", "me", or "assignee".
                            - Always pass the actual person the issue should be assigned to, e.g. "jdoe", "Jane Doe".
                            """
            )
            String jiraUserName
    ) {
    }

    /**
     * Looks up the Jira account ID for a given jiraUserName name/query.
     *
     * @param assigneeRequest The request used to look up a jiraUserName for jira issues
     * @return AssigneeIdLookupResponse with the account ID or null if not found
     */
    @SuppressWarnings("unused")
    @McpTool(name = ASSIGN_JIRA, description = TOOL_DESCRIPTION)
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-ASSIGNEE-LOOKUP')")
    public AssigneeResponse assigneeLookup(
            @McpToolParam(description = PARAM_DESCRIPTION)
            AssigneeRequest assigneeRequest) {
        log.debug("Invoking user search for: {}", assigneeRequest);

        if (assigneeRequest == null || StringUtils.isEmpty(assigneeRequest.jiraUserName())) {
            log.warn("Empty jiraUserName name provided");
            throw new JiraException("Empty jiraUserName name provided");
        }

        User user = jiraUserService.search(assigneeRequest.jiraUserName())
                .stream()
                .findFirst()
                .orElseThrow(() -> new JiraException("No assignable user found"));

        log.debug("Found jiraUserName with ID: {}", user.accountId());

        return new AssigneeResponse(user.accountId());
    }
}