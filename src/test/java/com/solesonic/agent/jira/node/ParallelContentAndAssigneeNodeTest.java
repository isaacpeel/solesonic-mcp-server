package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.model.AssigneeLookupResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParallelContentAndAssigneeNodeTest {

    private static final String USER_MESSAGE = "Create a login story for John";
    private static final String DETAILED_DESCRIPTION = "As a user, I want to log in, so that I can access my account";
    private static final String STORY_SUMMARY = "Add login capability";
    private static final List<String> ACCEPTANCE_CRITERIA = List.of("Given valid credentials, when I log in, then I am authenticated");
    private static final AssigneeLookupResult ASSIGNEE_LOOKUP_RESULT = new AssigneeLookupResult(true, "acc-1", "RESOLVED", "John");

    @Mock
    private GenerateDetailedDescriptionNode generateDetailedDescriptionNode;

    @Mock
    private GenerateStorySummaryNode generateStorySummaryNode;

    @Mock
    private GenerateAcceptanceCriteriaNode generateAcceptanceCriteriaNode;

    @Mock
    private ResolveAssigneeNode resolveAssigneeNode;

    private ParallelContentAndAssigneeNode node;

    private static JiraState stateWithUserMessage() {
        return new JiraState(Map.of(JiraState.USER_MESSAGE, USER_MESSAGE));
    }

    private void stubHappyPathContentChain() {
        when(generateDetailedDescriptionNode.apply(any(JiraState.class)))
                .thenReturn(completedFuture(Map.of(JiraState.DETAILED_DESCRIPTION, DETAILED_DESCRIPTION)));
        when(generateStorySummaryNode.apply(any(JiraState.class)))
                .thenReturn(completedFuture(Map.of(JiraState.STORY_SUMMARY, STORY_SUMMARY)));
        when(generateAcceptanceCriteriaNode.apply(any(JiraState.class)))
                .thenReturn(completedFuture(Map.of(JiraState.ACCEPTANCE_CRITERIA, ACCEPTANCE_CRITERIA)));
    }

    private void setUpNode() {
        node = new ParallelContentAndAssigneeNode(
                generateDetailedDescriptionNode, generateStorySummaryNode, generateAcceptanceCriteriaNode, resolveAssigneeNode);
    }

    @Test
    void apply_mergesContentChainAndAssigneeResults() throws Exception {
        stubHappyPathContentChain();
        when(resolveAssigneeNode.apply(any(JiraState.class))).thenReturn(completedFuture(Map.of(
                JiraState.ASSIGNEE_LOOKUP_RESULT, ASSIGNEE_LOOKUP_RESULT,
                JiraState.ASSIGNEE_NOT_RESOLVED, false)));
        setUpNode();

        Map<String, Object> updates = node.apply(stateWithUserMessage()).get(2, TimeUnit.SECONDS);

        assertThat(updates)
                .containsEntry(JiraState.DETAILED_DESCRIPTION, DETAILED_DESCRIPTION)
                .containsEntry(JiraState.STORY_SUMMARY, STORY_SUMMARY)
                .containsEntry(JiraState.ACCEPTANCE_CRITERIA, ACCEPTANCE_CRITERIA)
                .containsEntry(JiraState.ASSIGNEE_LOOKUP_RESULT, ASSIGNEE_LOOKUP_RESULT)
                .containsEntry(JiraState.ASSIGNEE_NOT_RESOLVED, false);
    }

    @Test
    void apply_contentChain_forwardsPriorStepOutputsToLaterSteps() throws Exception {
        when(resolveAssigneeNode.apply(any(JiraState.class))).thenReturn(completedFuture(Map.of(
                JiraState.ASSIGNEE_NOT_RESOLVED, true)));

        when(generateDetailedDescriptionNode.apply(any(JiraState.class)))
                .thenReturn(completedFuture(Map.of(JiraState.DETAILED_DESCRIPTION, DETAILED_DESCRIPTION)));

        ArgumentCaptor<JiraState> summaryStateCaptor = ArgumentCaptor.forClass(JiraState.class);
        when(generateStorySummaryNode.apply(summaryStateCaptor.capture()))
                .thenReturn(completedFuture(Map.of(JiraState.STORY_SUMMARY, STORY_SUMMARY)));

        ArgumentCaptor<JiraState> criteriaStateCaptor = ArgumentCaptor.forClass(JiraState.class);
        when(generateAcceptanceCriteriaNode.apply(criteriaStateCaptor.capture()))
                .thenReturn(completedFuture(Map.of(JiraState.ACCEPTANCE_CRITERIA, ACCEPTANCE_CRITERIA)));

        setUpNode();

        node.apply(stateWithUserMessage()).get(2, TimeUnit.SECONDS);

        assertThat(summaryStateCaptor.getValue().detailedDescription()).contains(DETAILED_DESCRIPTION);
        assertThat(criteriaStateCaptor.getValue().detailedDescription()).contains(DETAILED_DESCRIPTION);
        assertThat(criteriaStateCaptor.getValue().storySummary()).contains(STORY_SUMMARY);
    }

    @Test
    void apply_waitsForTheAssigneeBranchBeforeCompleting() throws Exception {
        stubHappyPathContentChain();
        CompletableFuture<Map<String, Object>> assigneeFuture = new CompletableFuture<>();
        when(resolveAssigneeNode.apply(any(JiraState.class))).thenReturn(assigneeFuture);
        setUpNode();

        CompletableFuture<Map<String, Object>> result = node.apply(stateWithUserMessage());

        assertThat(result).isNotDone();

        assigneeFuture.complete(Map.of(
                JiraState.ASSIGNEE_LOOKUP_RESULT, ASSIGNEE_LOOKUP_RESULT,
                JiraState.ASSIGNEE_NOT_RESOLVED, false));

        Map<String, Object> updates = result.get(2, TimeUnit.SECONDS);

        assertThat(updates)
                .containsEntry(JiraState.DETAILED_DESCRIPTION, DETAILED_DESCRIPTION)
                .containsEntry(JiraState.ASSIGNEE_LOOKUP_RESULT, ASSIGNEE_LOOKUP_RESULT);
    }

    @Test
    void apply_assigneeBranchFails_propagatesFailure() {
        stubHappyPathContentChain();
        when(resolveAssigneeNode.apply(any(JiraState.class)))
                .thenReturn(failedFuture(new RuntimeException("assignee resolution failed")));
        setUpNode();

        CompletableFuture<Map<String, Object>> result = node.apply(stateWithUserMessage());

        assertThat(result).failsWithin(2, TimeUnit.SECONDS);
    }

    @Test
    void apply_contentChainFails_propagatesFailure() {
        when(resolveAssigneeNode.apply(any(JiraState.class))).thenReturn(completedFuture(Map.of(
                JiraState.ASSIGNEE_NOT_RESOLVED, true)));
        when(generateDetailedDescriptionNode.apply(any(JiraState.class)))
                .thenReturn(failedFuture(new RuntimeException("description generation failed")));
        setUpNode();

        CompletableFuture<Map<String, Object>> result = node.apply(stateWithUserMessage());

        assertThat(result).failsWithin(2, TimeUnit.SECONDS);
    }
}
