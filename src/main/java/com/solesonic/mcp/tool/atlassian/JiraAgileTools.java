package com.solesonic.mcp.tool.atlassian;

import com.solesonic.mcp.model.atlassian.agile.Board;
import com.solesonic.mcp.model.atlassian.agile.BoardIssues;
import com.solesonic.mcp.model.atlassian.agile.Boards;
import com.solesonic.mcp.service.atlassian.JiraAgileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@SuppressWarnings("unused")
@Service
public class JiraAgileTools {
    private static final Logger log = LoggerFactory.getLogger(JiraAgileTools.class);

    public static final String LIST_JIRA_BOARDS = "list_jira_boards";
    public static final String GET_JIRA_BOARD = "get_jira_board";
    public static final String GET_JIRA_BOARD_CONFIGURATION = "get_jira_board_configuration";
    public static final String GET_JIRA_BOARD_ISSUES = "get_jira_board_issues";

    private static final String LIST_JIRA_BOARDS_DESCRIPTION = """
            Retrieves a paginated list of all Jira agile boards accessible to the user.
            Returns board metadata including IDs, names, types (Scrum/Kanban), and associated projects.
            Supports filtering by board type, name, project, or account, and pagination for large result sets.
            Use this when you need to discover available boards, find a specific board, or enumerate workspace boards.
            """;

    private static final String LIST_JIRA_BOARDS_INPUT_DESCRIPTION = """
            Optional filters and pagination settings for listing boards.
            All fields are optional: specify startAt/maxResults for pagination, type to filter by board type (scrum/kanban),
            name for text matching, projectKeyOrId to scope to a specific project, or accountId to filter by board owner.
            """;

    private static final String GET_JIRA_BOARD_DESCRIPTION = """
            Retrieves detailed information about a specific Jira agile board by its unique identifier.
            Returns board properties including name, type (Scrum or Kanban), location, and associated project details.
            Use this when you need to inspect board metadata, verify board existence, or gather board configuration context.
            """;

    private static final String GET_JIRA_BOARD_CONFIGURATION_DESCRIPTION = """
            Retrieves the complete configuration settings for a specific Jira agile board.
            Returns board configuration including columns, swimlanes, estimation settings, filters, and workflow mappings.
            Use this when you need to understand board structure, analyze workflow configuration, or audit board settings.
            """;

    private static final String BOARD_ID_DESCRIPTION = """
            The unique identifier of the Jira board to retrieve.
            This is typically a numeric string that can be found in board URLs or from the list_jira_boards tool.
            """;

    private static final String GET_JIRA_BOARD_ISSUES_DESCRIPTION = """
            Retrieves all issues from a specific Jira agile board (Scrum or Kanban).
            Returns issue details including keys, summaries, statuses, assignees, and other fields.
            Supports filtering via JQL (Jira Query Language) and pagination for large result sets.
            Use this when you need to analyze board contents, track sprint progress, or export issue data.
            """;

    private static final String GET_JIRA_BOARD_INPUT_DESCRIPTION = """
            Configuration for retrieving board issues.
            Must include the board ID (required).
            Optionally specify JQL filter, pagination (startAt/maxResults), and query validation.
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

    private final JiraAgileService jiraAgileService;

    public JiraAgileTools(JiraAgileService jiraAgileService) {
        this.jiraAgileService = jiraAgileService;
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
                                    String projectKeyOrId,
                                    @McpToolParam(required = false, description = ACCOUNT_ID_DESCRIPTION)
                                    String accountId){}

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

    @McpTool(name = LIST_JIRA_BOARDS, description = LIST_JIRA_BOARDS_DESCRIPTION)
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-AGILE-LIST')")
    public Boards listJiraBoards(@McpToolParam(description = LIST_JIRA_BOARDS_INPUT_DESCRIPTION) ListBoardsRequest listBoardsRequest) {
        log.info("Listing Jira boards: {}", listBoardsRequest);

        return jiraAgileService.listBoards(listBoardsRequest);
    }

    @McpTool(name = GET_JIRA_BOARD, description = GET_JIRA_BOARD_DESCRIPTION)
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-AGILE-GET')")
    public Board getJiraBoard(@McpToolParam(description = BOARD_ID_DESCRIPTION) String boardId) {
        log.info("Getting Jira board: {}", boardId);
        return jiraAgileService.getBoard(boardId);
    }

    @McpTool(name = GET_JIRA_BOARD_CONFIGURATION, description = GET_JIRA_BOARD_CONFIGURATION_DESCRIPTION)
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-AGILE-CONFIG')")
    public String getJiraBoardConfiguration(@McpToolParam(description = BOARD_ID_DESCRIPTION) String boardId) {
        log.info("Fetching configuration for Jira board: {}", boardId);
        return jiraAgileService.getBoardConfiguration(boardId);
    }

    @McpTool(name = GET_JIRA_BOARD_ISSUES, description = GET_JIRA_BOARD_ISSUES_DESCRIPTION)
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-AGILE-ISSUES')")
    public BoardIssues getJiraBoardIssues(@McpToolParam(description = GET_JIRA_BOARD_INPUT_DESCRIPTION) BoardIssuesRequest boardIssuesRequest) {
        log.info("Getting Jira board issues");

        if(boardIssuesRequest == null) {
            log.error("Board issues request is null.");
            throw new IllegalArgumentException("Board issues request is null");
        }

        if(boardIssuesRequest.boardId == null) {
            log.error("Board ID is null.");
            throw new IllegalArgumentException("Board ID is null");
        }

        log.info("Fetching issues for board: {}", boardIssuesRequest.boardId());
        BoardIssues boardIssues = jiraAgileService.getBoardIssues(boardIssuesRequest);

        log.info("Found Jira board issues");

        return boardIssues;
    }
}
