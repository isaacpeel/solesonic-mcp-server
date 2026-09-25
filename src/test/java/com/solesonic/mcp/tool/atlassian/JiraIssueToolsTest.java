package com.solesonic.mcp.tool.atlassian;

import com.solesonic.agent.checkpoint.GraphRunner;
import com.solesonic.agent.jira.JiraGraphConfig;
import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.agent.model.JiraIssueCreatePayload;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.jira.JiraIssue;
import com.solesonic.service.atlassian.JiraIssueService;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraIssueToolsTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));
    private static final String CHAT_ID = "chat-1";
    private static final String EXPECTED_THREAD_ID = "jira-create:" + CALLER.userId() + ":" + CHAT_ID;

    @Mock
    private JiraIssueService jiraIssueService;

    @Mock
    private CompiledGraph<JiraState> jiraCreateGraph;

    @Mock
    private JiraAssigneeElicitation jiraAssigneeElicitation;

    @Mock
    private GraphRunner graphRunner;

    @Mock
    private BaseCheckpointSaver graphCheckpointSaver;

    @Mock
    private McpSyncRequestContext mcpSyncRequestContext;

    private JiraIssueTools jiraIssueTools;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(CALLER.userId().toString())
                .issuedAt(Instant.now())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        when(mcpSyncRequestContext.requestMeta()).thenReturn(Map.of(JiraIssueTools.CHAT_ID, CHAT_ID));

        jiraIssueTools = new JiraIssueTools(jiraIssueService, jiraCreateGraph, jiraAssigneeElicitation, graphRunner, graphCheckpointSaver);
        ReflectionTestUtils.setField(jiraIssueTools, "jiraUrlTemplate", "https://example.atlassian.net/browse/{key}");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createJiraStory_freshRun_carriesTheCallerIntoTheGraphAndTheCreateCall() {
        AssigneeLookupResult assignee = new AssigneeLookupResult(true, "acc-1", "RESOLVED", "Bob");
        JiraIssueCreatePayload payload = new JiraIssueCreatePayload("Login", "Build it", List.of("It works"), assignee);
        JiraIssue convertedIssue = JiraIssue.fields(null).build();

        when(graphCheckpointSaver.get(any(RunnableConfig.class))).thenReturn(Optional.empty());
        when(graphRunner.run(eq(jiraCreateGraph), any(GraphInput.class), any(RunnableConfig.class),
                ArgumentMatchers.any()))
                .thenReturn(NodeOutput.of(END, new JiraState(Map.of(JiraState.FINAL_PAYLOAD, payload))));
        when(jiraIssueService.convert(payload)).thenReturn(convertedIssue);
        when(jiraIssueService.create(CALLER, convertedIssue)).thenReturn(JiraIssue.fields(null).key("ABC-1").build());

        String result = jiraIssueTools.createJiraStory(mcpSyncRequestContext, "Create a login story for Bob");

        assertThat(result).contains("ABC-1");

        ArgumentCaptor<GraphInput> graphInputCaptor = ArgumentCaptor.forClass(GraphInput.class);
        ArgumentCaptor<RunnableConfig> runnableConfigCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(graphRunner).run(eq(jiraCreateGraph), graphInputCaptor.capture(), runnableConfigCaptor.capture(),
                ArgumentMatchers.any());

        assertThat(graphInputCaptor.getValue()).isInstanceOfSatisfying(GraphArgs.class, graphArgs ->
                assertThat(graphArgs.value()).containsEntry(JiraState.CALLER_IDENTITY, CALLER));
        assertThat(runnableConfigCaptor.getValue().threadId()).contains(EXPECTED_THREAD_ID);
    }

    @Test
    void createJiraStory_assigneeElicitationFails_discardsThePausedRunAndRethrows() {
        IllegalStateException elicitationFailure = new IllegalStateException("client disconnected");

        when(graphCheckpointSaver.get(any(RunnableConfig.class))).thenReturn(Optional.empty());
        when(graphRunner.run(eq(jiraCreateGraph), any(GraphInput.class), any(RunnableConfig.class),
                ArgumentMatchers.any()))
                .thenReturn(NodeOutput.of(JiraGraphConfig.AWAIT_ASSIGNEE_SELECTION, new JiraState(Map.of())));
        when(jiraAssigneeElicitation.selectAssignee(eq(CALLER), eq(mcpSyncRequestContext), eq(List.of()), anyMap()))
                .thenThrow(elicitationFailure);

        assertThatThrownBy(() -> jiraIssueTools.createJiraStory(mcpSyncRequestContext, "Create a login story"))
                .isSameAs(elicitationFailure);

        ArgumentCaptor<RunnableConfig> discardedConfigCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(graphRunner).discard(discardedConfigCaptor.capture());
        assertThat(discardedConfigCaptor.getValue().threadId()).contains(EXPECTED_THREAD_ID);
    }

    @Test
    void createJiraStory_assigneeDeclined_discardsThePausedRun() {
        when(graphCheckpointSaver.get(any(RunnableConfig.class))).thenReturn(Optional.empty());
        when(graphRunner.run(eq(jiraCreateGraph), any(GraphInput.class), any(RunnableConfig.class),
                ArgumentMatchers.any()))
                .thenReturn(NodeOutput.of(JiraGraphConfig.AWAIT_ASSIGNEE_SELECTION, new JiraState(Map.of())));
        when(jiraAssigneeElicitation.selectAssignee(eq(CALLER), eq(mcpSyncRequestContext), eq(List.of()), anyMap()))
                .thenReturn(new JiraAssigneeElicitation.Selection.Declined());

        String result = jiraIssueTools.createJiraStory(mcpSyncRequestContext, "Create a login story");

        assertThat(result).contains("none was selected");

        ArgumentCaptor<RunnableConfig> discardedConfigCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(graphRunner).discard(discardedConfigCaptor.capture());
        assertThat(discardedConfigCaptor.getValue().threadId()).contains(EXPECTED_THREAD_ID);
    }
}
