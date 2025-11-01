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

    private final JiraAgileService jiraAgileService;

    public JiraAgileTools(JiraAgileService jiraAgileService) {
        this.jiraAgileService = jiraAgileService;
    }

    public record ListBoardsRequest(Integer startAt, Integer maxResults, String type, String name, String projectKeyOrId, String accountId){}

    public record BoardIssuesRequest(@McpToolParam(description = "The ID of the board to retrieve jira issues for.")
//                                     @JsonProperty("board_id")
                                     String boardId,
                                     @McpToolParam(required = false) String jql,
                                     @McpToolParam(required = false)
//                                     @JsonProperty("start_at")
                                     Integer startAt,
                                     @McpToolParam(required = false, description = "The number of issues to return.  Will default to 15.")
//                                     @JsonProperty("max_results")
                                     Integer maxResults,
                                     @McpToolParam
//                                     @JsonProperty("validated_query")
                                     boolean validateQuery) {}

    public record BoardBacklogIssuesRequest(String boardId, String jql, Integer startAt, Integer maxResults, Boolean validateQuery) {}

    @McpTool(name = LIST_JIRA_BOARDS, description = "Lists Jira agile boards with optional filters and pagination.")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-AGILE-LIST')")
    public Boards listJiraBoards(@McpToolParam(description = "Optional filters for listing boards.") ListBoardsRequest listBoardsRequest) {
        log.debug("Listing Jira boards: {}", listBoardsRequest);

        return jiraAgileService.listBoards(listBoardsRequest);
    }

    @McpTool(name = GET_JIRA_BOARD, description = "Gets a Jira board by its ID.")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-AGILE-GET')")
    public Board getJiraBoard(@McpToolParam(description = "Board ID") String boardId) {
        log.debug("Getting Jira board: {}", boardId);
        return jiraAgileService.getBoard(boardId);
    }

    @McpTool(name = GET_JIRA_BOARD_CONFIGURATION, description = "Gets the configuration for a Jira board by its ID.")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-AGILE-CONFIG')")
    public String getJiraBoardConfiguration(@McpToolParam(description = "Board ID") String boardId) {
        log.debug("Fetching configuration for Jira board: {}", boardId);
        return jiraAgileService.getBoardConfiguration(boardId);
    }

    @McpTool(name = GET_JIRA_BOARD_ISSUES, description = "Lists issues on a Jira board with optional JQL and pagination.")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-AGILE-ISSUES')")
    public BoardIssues getJiraBoardIssues(@McpToolParam(description = "Request for getting issue by board.  Information about the board to search.") BoardIssuesRequest boardIssuesRequest) {
        log.debug("Getting Jira board issues");

        if(boardIssuesRequest == null) {
            throw new IllegalArgumentException("Board issues request is null");
        }

        if(boardIssuesRequest.boardId == null) {
            throw new IllegalArgumentException("Board ID is null");
        }

        log.info("Fetching issues for board: {}", boardIssuesRequest.boardId());
        BoardIssues boardIssues = jiraAgileService.getBoardIssues(boardIssuesRequest);

        log.info("Found Jira board issues");

        return boardIssues;
    }
}
