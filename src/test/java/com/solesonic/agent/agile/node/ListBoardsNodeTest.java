package com.solesonic.agent.agile.node;

import com.solesonic.agent.agile.AgileState;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.agile.Board;
import com.solesonic.model.atlassian.agile.Boards;
import com.solesonic.service.atlassian.JiraAgileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListBoardsNodeTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    private JiraAgileService jiraAgileService;
    private ListBoardsNode node;

    @BeforeEach
    void setUp() {
        jiraAgileService = mock(JiraAgileService.class);
        node = new ListBoardsNode(jiraAgileService);
    }

    @Test
    void listsBoardsForTheStatesCaller_evenOnAPooledThread() throws Exception {
        List<Board> boards = List.of(new Board(1, "self", "Board", "scrum"));
        when(jiraAgileService.listBoards(CALLER)).thenReturn(new Boards(boards, 50, 1, true));
        AgileState state = new AgileState(Map.of(AgileState.CALLER_IDENTITY, CALLER));

        Map<String, Object> updates = CompletableFuture
                .supplyAsync(() -> node.apply(state), ForkJoinPool.commonPool())
                .join()
                .get();

        assertThat(updates).containsEntry(AgileState.BOARDS, boards);
    }

    @Test
    void missingCallerIdentity_failsWithoutCallingJira() {
        CompletableFuture<Map<String, Object>> result = node.apply(new AgileState(Map.of()));

        assertThat(result).isCompletedExceptionally();
        verifyNoInteractions(jiraAgileService);
    }
}
