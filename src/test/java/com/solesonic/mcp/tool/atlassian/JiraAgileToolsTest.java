package com.solesonic.mcp.tool.atlassian;

import com.solesonic.agent.agile.AgileQueryIntent;
import com.solesonic.agent.agile.AgileState;
import com.solesonic.agent.checkpoint.GraphRunner;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.agile.Board;
import com.solesonic.service.atlassian.JiraAgileService;
import org.bsc.langgraph4j.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraAgileToolsTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    @Mock
    private JiraAgileService jiraAgileService;

    @Mock
    private CompiledGraph<AgileState> agileGraph;

    @Mock
    private GraphRunner graphRunner;

    @Mock
    private McpSyncRequestContext mcpSyncRequestContext;

    private JiraAgileTools jiraAgileTools;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(CALLER.userId().toString())
                .issuedAt(Instant.now())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        jiraAgileTools = new JiraAgileTools(jiraAgileService, agileGraph, graphRunner);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void agileWorkflow_runsTheGraphForTheCallerAndAnswersWithTheSameCaller() {
        Board board = new Board(1, "self", "Board", "scrum");
        AgileQueryIntent countIntent = new AgileQueryIntent(List.of(), null, null, null, "", "COUNT", null, null);
        AgileState finalState = new AgileState(Map.of(
                AgileState.BOARDS, List.of(board),
                AgileState.AGILE_QUERY_INTENT, countIntent));

        when(graphRunner.run(eq(agileGraph), any(GraphInput.class), any(RunnableConfig.class),
                ArgumentMatchers.any()))
                .thenReturn(NodeOutput.of(END, finalState));
        when(jiraAgileService.handleCountQuery(CALLER, board, countIntent)).thenReturn("**Board** — 3 issues");

        String result = jiraAgileTools.agileWorkflow(mcpSyncRequestContext, "how many issues?");

        assertThat(result).isEqualTo("**Board** — 3 issues");

        ArgumentCaptor<GraphInput> graphInputCaptor = ArgumentCaptor.forClass(GraphInput.class);
        ArgumentCaptor<RunnableConfig> runnableConfigCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(graphRunner).run(eq(agileGraph), graphInputCaptor.capture(), runnableConfigCaptor.capture(),
                ArgumentMatchers.any());

        assertThat(graphInputCaptor.getValue()).isInstanceOfSatisfying(GraphArgs.class, graphArgs ->
                assertThat(graphArgs.value())
                        .containsEntry(AgileState.CALLER_IDENTITY, CALLER)
                        .containsEntry(AgileState.USER_MESSAGE, "how many issues?"));
        assertThat(runnableConfigCaptor.getValue().threadId()).hasValueSatisfying(threadId ->
                assertThat(threadId).startsWith("agile:" + CALLER.userId() + ":"));
    }
}
