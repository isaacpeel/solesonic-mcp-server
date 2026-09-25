package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.model.AssigneeCandidate;
import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.agent.model.AssigneeResolution;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.service.atlassian.AssigneeResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResolveAssigneeNodeTest {

    private static final String USER_MESSAGE = "Create a login story for John";
    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    private AssigneeResolutionService assigneeResolutionService;

    private ResolveAssigneeNode node;

    @BeforeEach
    void setUp() {
        assigneeResolutionService = mock(AssigneeResolutionService.class);
        node = new ResolveAssigneeNode(assigneeResolutionService);
    }

    private static JiraState stateWithUserMessage() {
        return new JiraState(Map.of(
                JiraState.USER_MESSAGE, USER_MESSAGE,
                JiraState.CALLER_IDENTITY, CALLER));
    }

    @Test
    void resolved_putsTheLookupResultOnTheState() throws Exception {
        AssigneeLookupResult assigneeLookupResult = new AssigneeLookupResult(true, "acc-1", "RESOLVED", "John");
        when(assigneeResolutionService.resolve(CALLER, USER_MESSAGE)).thenReturn(new AssigneeResolution.Resolved(assigneeLookupResult));

        Map<String, Object> updates = node.apply(stateWithUserMessage()).get();

        assertThat(updates)
                .containsEntry(JiraState.ASSIGNEE_LOOKUP_RESULT, assigneeLookupResult)
                .containsEntry(JiraState.ASSIGNEE_NOT_RESOLVED, false)
                .doesNotContainKey(JiraState.ASSIGNEE_CANDIDATES);
    }

    @Test
    void ambiguous_marksUnresolvedAndCarriesTheMatches() throws Exception {
        List<AssigneeCandidate> candidates = List.of(
                new AssigneeCandidate("acc-1", "John Smith"),
                new AssigneeCandidate("acc-2", "John Doe")
        );
        when(assigneeResolutionService.resolve(CALLER, USER_MESSAGE)).thenReturn(new AssigneeResolution.Ambiguous("John", candidates));

        Map<String, Object> updates = node.apply(stateWithUserMessage()).get();

        assertThat(updates)
                .containsEntry(JiraState.ASSIGNEE_NOT_RESOLVED, true)
                .containsEntry(JiraState.ASSIGNEE_CANDIDATES, candidates)
                .doesNotContainKey(JiraState.ASSIGNEE_LOOKUP_RESULT);
    }

    @Test
    void notFound_marksUnresolvedWithoutCandidates() throws Exception {
        when(assigneeResolutionService.resolve(CALLER, USER_MESSAGE)).thenReturn(new AssigneeResolution.NotFound("Zed"));

        Map<String, Object> updates = node.apply(stateWithUserMessage()).get();

        assertThat(updates)
                .containsEntry(JiraState.ASSIGNEE_NOT_RESOLVED, true)
                .doesNotContainKey(JiraState.ASSIGNEE_CANDIDATES);
    }

    @Test
    void notRequested_marksUnresolvedWithoutCandidates() throws Exception {
        when(assigneeResolutionService.resolve(CALLER, USER_MESSAGE)).thenReturn(new AssigneeResolution.NotRequested());

        Map<String, Object> updates = node.apply(stateWithUserMessage()).get();

        assertThat(updates)
                .containsEntry(JiraState.ASSIGNEE_NOT_RESOLVED, true)
                .doesNotContainKey(JiraState.ASSIGNEE_CANDIDATES);
    }

    @Test
    void missingUserMessage_fails() {
        CompletableFuture<Map<String, Object>> result = node.apply(new JiraState(Map.of(JiraState.CALLER_IDENTITY, CALLER)));

        assertThat(result).isCompletedExceptionally();
    }

    @Test
    void missingCallerIdentity_failsWithoutCallingJira() {
        CompletableFuture<Map<String, Object>> result = node.apply(new JiraState(Map.of(JiraState.USER_MESSAGE, USER_MESSAGE)));

        assertThat(result).isCompletedExceptionally();
        verifyNoInteractions(assigneeResolutionService);
    }
}
