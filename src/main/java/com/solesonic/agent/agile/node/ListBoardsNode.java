package com.solesonic.agent.agile.node;

import com.solesonic.agent.agile.AgileState;
import com.solesonic.service.atlassian.JiraAgileService;
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
    public CompletableFuture<Map<String, Object>> apply(AgileState agileState) {
        try {
            var boards = jiraAgileService.listBoards();
            int boardCount = boards.values().size();
            log.info("Found {} accessible boards", boardCount);

            return completedFuture(Map.of(AgileState.BOARDS, boards.values()));
        } catch (Exception exception) {
            log.error("Failed to list boards", exception);
            return failedFuture(exception);
        }
    }
}
