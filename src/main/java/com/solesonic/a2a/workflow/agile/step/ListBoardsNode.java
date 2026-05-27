package com.solesonic.a2a.workflow.agile.step;

import com.solesonic.mcp.service.atlassian.JiraAgileService;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import com.solesonic.a2a.workflow.agile.AgileState;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class ListBoardsNode implements AsyncNodeAction<AgileState> {

    private static final Logger log = LoggerFactory.getLogger(ListBoardsNode.class);

    private final JiraAgileService jiraAgileService;

    public ListBoardsNode(JiraAgileService jiraAgileService) {
        this.jiraAgileService = jiraAgileService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(AgileState state) {
        try {
            JiraAgileTools.ListBoardsRequest listBoardsRequest = new JiraAgileTools.ListBoardsRequest(
                    null, null, null, null, null
            );

            var boards = jiraAgileService.listBoards(listBoardsRequest);
            int boardCount = boards.values().size();
            log.info("Found {} accessible boards", boardCount);

            return completedFuture(Map.of(AgileState.BOARDS, boards.values()));
        } catch (Exception exception) {
            log.error("Failed to list boards", exception);
            return failedFuture(exception);
        }
    }
}
