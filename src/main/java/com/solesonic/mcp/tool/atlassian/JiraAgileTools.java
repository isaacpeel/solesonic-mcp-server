package com.solesonic.mcp.tool.atlassian;

import com.solesonic.mcp.service.atlassian.JiraAgileService;
import com.solesonic.mcp.tool.provider.AgileMetaProvider;
import com.solesonic.mcp.workflow.AgileQueryWorkflow;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@SuppressWarnings("unused")
@Service
public class JiraAgileTools {
    private static final Logger log = LoggerFactory.getLogger(JiraAgileTools.class);

    public static final String AGILE_WORKFLOW = "agile-workflow";

    private static final String BOARD_ID_DESCRIPTION = """
            The unique identifier of the Jira board to retrieve.
            This is typically a numeric string that can be found in board URLs or from the list_jira_boards tool.
            """;

    private static final String START_AT_DESCRIPTION = "The starting index for pagination (0-based). Use this to retrieve subsequent pages of results.";
    private static final String MAX_RESULTS_DESCRIPTION = "The maximum number of items to return per page. If not specified, defaults to the API's default limit.";
    private static final String BOARD_TYPE_DESCRIPTION = "Filter boards by type. Valid values: 'scrum' for Scrum boards, 'kanban' for Kanban boards. Omit to return all types.";
    private static final String BOARD_NAME_DESCRIPTION = "Filter boards by name using text matching. Partial matches are supported.";
    private static final String PROJECT_KEY_OR_ID_DESCRIPTION = "Filter boards by project using either the project key (e.g., 'PROJ') or numeric project ID.";
    private static final String ACCOUNT_ID_DESCRIPTION = "Filter boards by the Atlassian account ID of the board administrator or owner.";
    private static final String JQL_DESCRIPTION = "Optional Jira Query Language (JQL) expression to filter the returned issues. Allows complex filtering beyond board scope.";
    private static final String JQL_START_AT_DESCRIPTION = "The starting index for paginating through board issues (0-based). Defaults to 0 if not specified.";
    private static final String BOARD_ISSUES_MAX_RESULTS_DESCRIPTION = "The maximum number of issues to return per page. If not specified, defaults to 15.";
    private static final String VALIDATE_QUERY_DESCRIPTION = "Whether to validate the JQL query syntax before execution. Set to true to catch JQL errors early.";

    private static final String AGILE_WORKFLOW_DESCRIPTION = """
            A guided workflow to assist with agile boards.
            """;

    private final JiraAgileService jiraAgileService;
    private final AgileQueryWorkflow agileQueryWorkflow;

    public JiraAgileTools(JiraAgileService jiraAgileService, AgileQueryWorkflow agileQueryWorkflow) {
        this.jiraAgileService = jiraAgileService;
        this.agileQueryWorkflow = agileQueryWorkflow;
    }


    public record ListBoardsRequest(@McpToolParam(required = false, description = START_AT_DESCRIPTION)
                                    Integer startAt,
                                    @McpToolParam(required = false, description = MAX_RESULTS_DESCRIPTION)
                                    Integer maxResults,
                                    @McpToolParam(required = false, description = BOARD_TYPE_DESCRIPTION)
                                    String type,
                                    @McpToolParam(required = false, description = BOARD_NAME_DESCRIPTION)
                                    String name,
                                    @McpToolParam(required = false, description = PROJECT_KEY_OR_ID_DESCRIPTION)
                                    String projectKeyOrId){}

    public record BoardIssuesRequest(@McpToolParam(description = BOARD_ID_DESCRIPTION)
                                     String boardId,
                                     @McpToolParam(required = false, description = JQL_DESCRIPTION)
                                     String jql,
                                     @McpToolParam(required = false, description = JQL_START_AT_DESCRIPTION)
                                     Integer startAt,
                                     @McpToolParam(required = false, description = BOARD_ISSUES_MAX_RESULTS_DESCRIPTION)
                                     Integer maxResults,
                                     @McpToolParam(description = VALIDATE_QUERY_DESCRIPTION)
                                     boolean validateQuery) {}

    public record BoardBacklogIssuesRequest(String boardId, String jql, Integer startAt, Integer maxResults, Boolean validateQuery) {}

    @McpTool(name = AGILE_WORKFLOW, description = AGILE_WORKFLOW_DESCRIPTION, metaProvider = AgileMetaProvider.class)
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-AGILE-LIST')")
    public String agileWorkflow(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = "The user's natural language question about their agile boards or issues.") String userMessage
    ) {
        AgileQueryWorkflowContext workflowContext = agileQueryWorkflow.startWorkflow(mcpSyncRequestContext, userMessage);
        return jiraAgileService.handleBoardSelection(mcpSyncRequestContext, workflowContext);
    }
}
