package com.solesonic.mcp.tool.atlassian;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.agent.agile.AgileGraphConfig;
import com.solesonic.agent.agile.AgileQueryResult;
import com.solesonic.agent.agile.AgileState;
import com.solesonic.mcp.model.atlassian.agile.Board;
import com.solesonic.mcp.service.atlassian.JiraAgileService;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.bsc.langgraph4j.GraphDefinition.END;

@SuppressWarnings("unused")
@Service
public class JiraAgileTools {
    private static final Logger log = LoggerFactory.getLogger(JiraAgileTools.class);

    public static final String AGILE_WORKFLOW = "agile_workflow";

    private static final String BOARD_ID_DESCRIPTION = """
            The unique identifier of the Jira board to retrieve.
            This is typically a numeric string that can be found in board URLs or from the list_jira_boards tool.
            """;

    private static final String START_AT_DESCRIPTION = "The starting index for pagination (0-based). Use this to retrieve subsequent pages of results.";
    private static final String MAX_RESULTS_DESCRIPTION = "The maximum number of items to return per page. If not specified, defaults to the API's default limit.";
    private static final String BOARD_TYPE_DESCRIPTION = "Filter boards by type. Valid values: 'scrum' for Scrum boards, 'kanban' for Kanban boards. Omit to return all types.";
    private static final String BOARD_NAME_DESCRIPTION = "Filter boards by name using text matching. Partial matches are supported.";
    private static final String PROJECT_KEY_OR_ID_DESCRIPTION = "Filter boards by project using either the project key (e.g., 'PROJ') or numeric project ID.";
    private static final String JQL_DESCRIPTION = "Optional Jira Query Language (JQL) expression to filter the returned issues. Allows complex filtering beyond board scope.";
    private static final String JQL_START_AT_DESCRIPTION = "The starting index for paginating through board issues (0-based). Defaults to 0 if not specified.";
    private static final String BOARD_ISSUES_MAX_RESULTS_DESCRIPTION = "The maximum number of issues to return per page. If not specified, defaults to 15.";
    private static final String VALIDATE_QUERY_DESCRIPTION = "Whether to validate the JQL query syntax before execution. Set to true to catch JQL errors early.";

    private static final String AGILE_WORKFLOW_DESCRIPTION = """
            A guided workflow to assist with agile boards.
            """;

    private final JiraAgileService jiraAgileService;
    private final CompiledGraph<AgileState> agileResearchGraph;

    public JiraAgileTools(JiraAgileService jiraAgileService, CompiledGraph<AgileState> agileResearchGraph) {
        this.jiraAgileService = jiraAgileService;
        this.agileResearchGraph = agileResearchGraph;
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
                                    String projectKeyOrId) {}

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

    @McpTool(name = AGILE_WORKFLOW,
             description = AGILE_WORKFLOW_DESCRIPTION)
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-AGILE-LIST')")
    public String agileWorkflow(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = "The user's natural language question about their agile boards or issues.") String userMessage
    ) {
        String conversationId = extractConversationId(mcpSyncRequestContext);

        ProgressReporter progressReporter = new ProgressReporter(mcpSyncRequestContext);
        progressReporter.emit(5, "Parsing intent and loading boards…");

        Map<String, Object> graphInput = Map.of(
                AgileState.USER_MESSAGE, userMessage,
                AgileState.CONVERSATION_ID, conversationId
        );

        AtomicReference<AgileState> finalStateRef = new AtomicReference<>();

        agileResearchGraph.stream(graphInput, RunnableConfig.builder().build())
                .forEachAsync(output -> {
                    finalStateRef.set(output.state());

                    int progressPercent = switch (output.node()) {
                        case AgileGraphConfig.PARSE_AND_FETCH -> 50;
                        case AgileGraphConfig.ASSESS_SCOPE    -> 80;
                        case END                              -> 100;
                        default                               -> 10;
                    };
                    progressReporter.emit(progressPercent, "Completed: " + output.node());
                })
                .join();

        AgileState finalState = finalStateRef.get();
        AgileQueryResult queryResult = finalState.agileQueryResult().orElseThrow(
                () -> new IllegalStateException("Graph completed without an agile query result"));

        Board board = requireFirstBoard(finalState);
        String resolvedUserMessage = finalState.userMessage().orElse(userMessage);

        return switch (queryResult.queryType().toUpperCase()) {
            case "COUNT"      -> jiraAgileService.handleCountQuery(board, queryResult);
            case "LIST"       -> jiraAgileService.handleListQuery(
                                        mcpSyncRequestContext, board, queryResult, resolvedUserMessage);
            case "TRANSITION" -> jiraAgileService.handleTransitionQuery(
                                        mcpSyncRequestContext, board, queryResult, finalState);
            default           -> "Unrecognised query type: " + queryResult.queryType();
        };
    }

    private static String extractConversationId(McpSyncRequestContext mcpSyncRequestContext) {
        Map<String, Object> meta = mcpSyncRequestContext.requestMeta();
        if (meta != null && meta.containsKey("chatId")) {
            return meta.get("chatId").toString();
        }
        return UUID.randomUUID().toString();
    }

    @SuppressWarnings("unchecked")
    private Board requireFirstBoard(AgileState state) {
        List<Board> boards = (List<Board>) state.boards()
                .map(List.class::cast)
                .orElse(List.of());
        if (boards.isEmpty()) {
            throw new IllegalStateException("No Jira boards are accessible.");
        }
        return boards.getFirst();
    }
}
