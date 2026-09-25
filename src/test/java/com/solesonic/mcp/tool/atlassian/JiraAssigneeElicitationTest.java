package com.solesonic.mcp.tool.atlassian;

import com.solesonic.agent.model.AssigneeCandidate;
import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.service.atlassian.AssigneeResolutionService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.mcp.annotation.context.DefaultMcpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JiraAssigneeElicitationTest {

    private static final String CHAT_ID = "chatId";
    private static final Map<String, Object> META = Map.of(CHAT_ID, "test-chat-id");
    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    private static final List<AssigneeCandidate> ALL_ASSIGNABLE = List.of(
            new AssigneeCandidate("acc-1", "Bob"),
            new AssigneeCandidate("acc-2", "Alice")
    );

    private McpSyncServerExchange exchange;

    private McpSyncRequestContext context;

    private AssigneeResolutionService assigneeResolutionService;

    private JiraAssigneeElicitation jiraAssigneeElicitation;

    @BeforeEach
    void setUp() {
        exchange = mock(McpSyncServerExchange.class);

        context = DefaultMcpSyncRequestContext.builder()
                .request(CallToolRequest.builder("create_jira_story").build())
                .exchange(exchange)
                .build();

        ClientCapabilities capabilities = mock(ClientCapabilities.class);
        when(capabilities.elicitation()).thenReturn(mock(ClientCapabilities.Elicitation.class));
        when(exchange.getClientCapabilities()).thenReturn(capabilities);

        assigneeResolutionService = mock(AssigneeResolutionService.class);
        jiraAssigneeElicitation = new JiraAssigneeElicitation(assigneeResolutionService);
    }

    private void userAnswers(ElicitResult.Action action, Map<String, Object> content) {
        when(exchange.createElicitation(any(ElicitRequest.class))).thenReturn(new ElicitResult(action, content, null));
    }

    private ElicitFormRequest sentRequest() {
        ArgumentCaptor<ElicitFormRequest> captor = ArgumentCaptor.forClass(ElicitFormRequest.class);
        verify(exchange).createElicitation(captor.capture());
        return captor.getValue();
    }

    private static Map<String, Object> expectedSchema(List<Map<String, Object>> choices) {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        JiraAssigneeElicitation.ASSIGNEE_ACCOUNT_ID, Map.of(
                                "type", "string",
                                "title", "Assignee",
                                "oneOf", choices
                        )
                ),
                "required", List.of(JiraAssigneeElicitation.ASSIGNEE_ACCOUNT_ID)
        );
    }

    @Test
    void noMatches_offersEveryAssignableUser_andReturnsTheChosenOne() {
        when(assigneeResolutionService.listAssigneeCandidates(CALLER)).thenReturn(ALL_ASSIGNABLE);
        userAnswers(ElicitResult.Action.ACCEPT, Map.of(JiraAssigneeElicitation.ASSIGNEE_ACCOUNT_ID, "acc-2"));

        JiraAssigneeElicitation.Selection selection = jiraAssigneeElicitation.selectAssignee(CALLER, context,List.of(), META);

        assertThat(selection).isEqualTo(new JiraAssigneeElicitation.Selection.Selected(
                new AssigneeLookupResult(true, "acc-2", JiraAssigneeElicitation.USER_SELECTED, "Alice")));

        ElicitFormRequest sentRequest = sentRequest();
        assertThat(sentRequest.message()).contains("Who should this story be assigned to?");
        assertThat(sentRequest.meta()).isEqualTo(META);
        assertThat(sentRequest.requestedSchema()).isEqualTo(expectedSchema(List.of(
                Map.of("const", "acc-1", "title", "Bob"),
                Map.of("const", "acc-2", "title", "Alice")
        )));
    }

    @Test
    void ambiguousMatches_offerOnlyTheMatches() {
        List<AssigneeCandidate> matches = List.of(
                new AssigneeCandidate("acc-3", "John Smith"),
                new AssigneeCandidate("acc-4", "John Doe")
        );
        userAnswers(ElicitResult.Action.ACCEPT, Map.of(JiraAssigneeElicitation.ASSIGNEE_ACCOUNT_ID, "acc-3"));

        JiraAssigneeElicitation.Selection selection = jiraAssigneeElicitation.selectAssignee(CALLER, context,matches, META);

        assertThat(selection).isEqualTo(new JiraAssigneeElicitation.Selection.Selected(
                new AssigneeLookupResult(true, "acc-3", JiraAssigneeElicitation.USER_SELECTED, "John Smith")));
        verify(assigneeResolutionService, never()).listAssigneeCandidates(any());

        ElicitFormRequest sentRequest = sentRequest();
        assertThat(sentRequest.message()).contains("More than one");
        assertThat(sentRequest.requestedSchema()).isEqualTo(expectedSchema(List.of(
                Map.of("const", "acc-3", "title", "John Smith"),
                Map.of("const", "acc-4", "title", "John Doe")
        )));
    }

    @Test
    void candidateWithoutADisplayName_isTitledByItsAccountId() {
        when(assigneeResolutionService.listAssigneeCandidates(CALLER)).thenReturn(List.of(new AssigneeCandidate("acc-9", null)));
        userAnswers(ElicitResult.Action.ACCEPT, Map.of(JiraAssigneeElicitation.ASSIGNEE_ACCOUNT_ID, "acc-9"));

        JiraAssigneeElicitation.Selection selection = jiraAssigneeElicitation.selectAssignee(CALLER, context,List.of(), META);

        assertThat(selection).isEqualTo(new JiraAssigneeElicitation.Selection.Selected(
                new AssigneeLookupResult(true, "acc-9", JiraAssigneeElicitation.USER_SELECTED, "acc-9")));
        assertThat(sentRequest().requestedSchema()).isEqualTo(expectedSchema(List.of(
                Map.of("const", "acc-9", "title", "acc-9")
        )));
    }

    @Test
    void decline_isDeclined() {
        when(assigneeResolutionService.listAssigneeCandidates(CALLER)).thenReturn(ALL_ASSIGNABLE);
        userAnswers(ElicitResult.Action.DECLINE, null);

        JiraAssigneeElicitation.Selection selection = jiraAssigneeElicitation.selectAssignee(CALLER, context,List.of(), META);

        assertThat(selection).isEqualTo(new JiraAssigneeElicitation.Selection.Declined());
    }

    @Test
    void cancel_isCancelled() {
        when(assigneeResolutionService.listAssigneeCandidates(CALLER)).thenReturn(ALL_ASSIGNABLE);
        userAnswers(ElicitResult.Action.CANCEL, null);

        JiraAssigneeElicitation.Selection selection = jiraAssigneeElicitation.selectAssignee(CALLER, context,List.of(), META);

        assertThat(selection).isEqualTo(new JiraAssigneeElicitation.Selection.Cancelled());
    }

    @Test
    void noAssignableUsers_neverPromptsTheUser() {
        when(assigneeResolutionService.listAssigneeCandidates(CALLER)).thenReturn(List.of());

        JiraAssigneeElicitation.Selection selection = jiraAssigneeElicitation.selectAssignee(CALLER, context,List.of(), META);

        assertThat(selection).isEqualTo(new JiraAssigneeElicitation.Selection.NoCandidates());
        verify(exchange, never()).createElicitation(any(ElicitRequest.class));
    }

    @Test
    void acceptWithAnAccountIdThatWasNotOffered_isInvalid() {
        when(assigneeResolutionService.listAssigneeCandidates(CALLER)).thenReturn(ALL_ASSIGNABLE);
        userAnswers(ElicitResult.Action.ACCEPT, Map.of(JiraAssigneeElicitation.ASSIGNEE_ACCOUNT_ID, "acc-unknown"));

        JiraAssigneeElicitation.Selection selection = jiraAssigneeElicitation.selectAssignee(CALLER, context,List.of(), META);

        assertThat(selection).isEqualTo(new JiraAssigneeElicitation.Selection.InvalidSelection("acc-unknown"));
    }

    @Test
    void acceptWithoutContent_isInvalid() {
        when(assigneeResolutionService.listAssigneeCandidates(CALLER)).thenReturn(ALL_ASSIGNABLE);
        userAnswers(ElicitResult.Action.ACCEPT, null);

        JiraAssigneeElicitation.Selection selection = jiraAssigneeElicitation.selectAssignee(CALLER, context,List.of(), META);

        assertThat(selection).isEqualTo(new JiraAssigneeElicitation.Selection.InvalidSelection(null));
    }
}
