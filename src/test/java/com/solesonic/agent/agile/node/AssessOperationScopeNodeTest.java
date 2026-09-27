package com.solesonic.agent.agile.node;

import com.solesonic.agent.agile.AgileQueryIntent;
import com.solesonic.agent.agile.AgileState;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import com.solesonic.model.atlassian.agile.Board;
import com.solesonic.model.atlassian.agile.BoardIssues;
import com.solesonic.service.atlassian.JiraAgileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssessOperationScopeNodeTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));
    private static final AgileQueryIntent TRANSITION_INTENT = new AgileQueryIntent(
            List.of(), "currentUser()", null, null, "", "TRANSITION", 0, "Done");
    private static final List<Board> BOARDS = List.of(new Board(1, "self", "Board", "scrum"));

    private JiraAgileService jiraAgileService;
    private AssessOperationScopeNode node;

    @BeforeEach
    void setUp() {
        jiraAgileService = mock(JiraAgileService.class);
        node = new AssessOperationScopeNode(jiraAgileService);
    }

    @Test
    void countsMatchingIssuesForTheStatesCaller() throws Exception {
        when(jiraAgileService.getBoardIssues(eq(CALLER), any(JiraAgileTools.BoardIssuesRequest.class)))
                .thenReturn(new BoardIssues(null, 0, 0, 25, List.of()));
        AgileState state = new AgileState(Map.of(
                AgileState.CALLER_IDENTITY, CALLER,
                AgileState.AGILE_QUERY_INTENT, TRANSITION_INTENT,
                AgileState.BOARDS, BOARDS));

        Map<String, Object> updates = node.apply(state).get();

        assertThat(updates)
                .containsEntry(AgileState.ESTIMATED_ITEM_COUNT, 25)
                .containsEntry(AgileState.REQUIRES_BATCHING, true);
    }

    @Test
    void missingCallerIdentity_failsWithoutCallingJira() {
        AgileState state = new AgileState(Map.of(
                AgileState.AGILE_QUERY_INTENT, TRANSITION_INTENT,
                AgileState.BOARDS, BOARDS));

        CompletableFuture<Map<String, Object>> result = node.apply(state);

        assertThat(result).isCompletedExceptionally();
        verifyNoInteractions(jiraAgileService);
    }
}
