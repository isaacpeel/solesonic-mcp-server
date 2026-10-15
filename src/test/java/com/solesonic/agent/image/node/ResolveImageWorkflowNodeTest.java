package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageWorkflowSelection;
import com.solesonic.agent.image.ImageWorkflowState;
import com.solesonic.agent.image.WorkflowCandidate;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ResolveImageWorkflowNodeTest {

    private static final WorkflowCandidate FIRST = new WorkflowCandidate("first_workflow", "First", "General.");
    private static final WorkflowCandidate SECOND = new WorkflowCandidate("second_workflow", "Second", "People.");

    private final ResolveImageWorkflowNode resolveImageWorkflowNode = new ResolveImageWorkflowNode();

    @Test
    void apply_validSelection_isKeptUnchanged() throws Exception {
        ImageWorkflowSelection routerSelection = new ImageWorkflowSelection("second_workflow", null, "Edits a person.");

        ImageWorkflowSelection resolved = resolve(List.of(FIRST, SECOND), routerSelection);

        assertThat(resolved).isEqualTo(routerSelection);
    }

    @Test
    void apply_nullToolName_usesTheFirstCandidateAndSaysSo() throws Exception {
        ImageWorkflowSelection resolved = resolve(List.of(SECOND, FIRST),
                new ImageWorkflowSelection(null, null, "The routing model failed."));

        assertThat(resolved.toolName()).isEqualTo("second_workflow");
        assertThat(resolved.rationale()).contains("The routing model failed.", "first candidate");
    }

    @Test
    void apply_unknownToolName_usesTheFirstCandidateAndNamesTheRejectedPick() throws Exception {
        ImageWorkflowSelection resolved = resolve(List.of(FIRST, SECOND),
                new ImageWorkflowSelection("made_up_workflow", null, "Guessing."));

        assertThat(resolved.toolName()).isEqualTo("first_workflow");
        assertThat(resolved.rationale()).contains("made_up_workflow");
    }

    @Test
    void apply_noSelectionOnTheState_fallsBackRatherThanFailing() throws Exception {
        ImageWorkflowSelection resolved = selection(resolveImageWorkflowNode.apply(
                new ImageWorkflowState(stateData(List.of(FIRST)))).get());

        assertThat(resolved.toolName()).isEqualTo("first_workflow");
    }

    @Test
    void apply_noCandidates_failsWithAClearMessage() {
        Map<String, Object> stateData = new HashMap<>(stateData(List.of()));
        stateData.put(ImageWorkflowState.REFERENCE_IMAGE_COUNT, 2);

        CompletableFuture<Map<String, Object>> future = resolveImageWorkflowNode.apply(new ImageWorkflowState(stateData));

        assertThat(future).isCompletedExceptionally();
        assertThat(catchThrowable(future::join))
                .rootCause()
                .hasMessageContaining("No image workflow accepts 2 reference image(s)");
    }

    private ImageWorkflowSelection resolve(List<WorkflowCandidate> candidates,
                                           ImageWorkflowSelection routerSelection) throws Exception {
        Map<String, Object> stateData = new HashMap<>(stateData(candidates));
        stateData.put(ImageWorkflowState.WORKFLOW_SELECTION, routerSelection);

        return selection(resolveImageWorkflowNode.apply(new ImageWorkflowState(stateData)).get());
    }

    private static Map<String, Object> stateData(List<WorkflowCandidate> candidates) {
        return Map.of(
                ImageWorkflowState.USER_PROMPT, "a lighthouse",
                ImageWorkflowState.REFERENCE_IMAGE_COUNT, 0,
                ImageWorkflowState.CANDIDATE_WORKFLOWS, candidates);
    }

    private static ImageWorkflowSelection selection(Map<String, Object> result) {
        return new ImageWorkflowState(result).workflowSelection().orElseThrow();
    }
}
