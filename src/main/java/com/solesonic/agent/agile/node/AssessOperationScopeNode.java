package com.solesonic.agent.agile.node;

import com.solesonic.agent.agile.AgileQueryIntent;
import com.solesonic.agent.agile.AgileState;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import com.solesonic.model.atlassian.agile.Board;
import com.solesonic.service.atlassian.JiraAgileService;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

@Component
public class AssessOperationScopeNode implements AsyncNodeAction<AgileState> {

    private static final Logger log = LoggerFactory.getLogger(AssessOperationScopeNode.class);

    static final int BATCH_THRESHOLD = 20;
    static final int DEFAULT_BATCH_SIZE = 20;

    private final JiraAgileService jiraAgileService;

    public AssessOperationScopeNode(JiraAgileService jiraAgileService) {
        this.jiraAgileService = jiraAgileService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(AgileState state) {
        AgileQueryIntent agileQueryIntent = state.agileQueryResult().orElse(null);

        if (agileQueryIntent == null || !agileQueryIntent.isTransitionQuery()) {
            log.debug("Scope assessment skipped — not a transition query");
            return completedFuture(Map.of());
        }

        List<Board> boards = state.boards()
                .orElse(List.of());

        if (boards.isEmpty()) {
            log.debug("Scope assessment skipped — no boards available");
            return completedFuture(Map.of());
        }

        Board board = boards.getFirst();
        String jqlFilter = agileQueryIntent.jqlFilter() == null ? "" : agileQueryIntent.jqlFilter().strip();

        JiraAgileTools.BoardIssuesRequest boardIssuesRequest = new JiraAgileTools.BoardIssuesRequest(
                String.valueOf(board.id()),
                jqlFilter.isEmpty() ? null : jqlFilter,
                null,
                0,
                false
        );

        var boardIssues = jiraAgileService.getBoardIssues(boardIssuesRequest);
        int totalCount = boardIssues.total() != null ? boardIssues.total() : 0;
        boolean needsBatching = totalCount > BATCH_THRESHOLD;

        log.info("Scope assessment: {} items found, batching={}", totalCount, needsBatching);

        return completedFuture(Map.of(
                AgileState.ESTIMATED_ITEM_COUNT, totalCount,
                AgileState.REQUIRES_BATCHING, needsBatching,
                AgileState.BATCH_SIZE, DEFAULT_BATCH_SIZE
        ));
    }
}
