package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageRoutingProperties;
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

    private static final String DEFAULT_WORKFLOW = "generate_image";

    private static final WorkflowCandidate GENERATE_IMAGE = new WorkflowCandidate("generate_image", "FLUX", "General.");
    private static final WorkflowCandidate PERSON_REFERENCE = new WorkflowCandidate("person_reference_image", "Kontext", "People.");

    private final ResolveImageWorkflowNode resolveImageWorkflowNode =
            new ResolveImageWorkflowNode(new ImageRoutingProperties(DEFAULT_WORKFLOW, Map.of()));

    @Test
    void apply_validSelection_isKeptUnchanged() throws Exception {
        ImageWorkflowSelection routerSelection = new ImageWorkflowSelection("person_reference_image", null, "Edits a person.");

        ImageWorkflowSelection resolved = resolve(List.of(GENERATE_IMAGE, PERSON_REFERENCE), routerSelection);

        assertThat(resolved).isEqualTo(routerSelection);
    }

    @Test
    void apply_nullToolName_usesTheDefaultWorkflowAndSaysSo() throws Exception {
        ImageWorkflowSelection resolved = resolve(List.of(PERSON_REFERENCE, GENERATE_IMAGE),
                new ImageWorkflowSelection(null, null, "The routing model failed."));

        assertThat(resolved.toolName()).isEqualTo(DEFAULT_WORKFLOW);
        assertThat(resolved.rationale()).contains("The routing model failed.", "default workflow");
    }

    @Test
    void apply_unknownToolName_usesTheDefaultWorkflowAndNamesTheRejectedPick() throws Exception {
        ImageWorkflowSelection resolved = resolve(List.of(GENERATE_IMAGE, PERSON_REFERENCE),
                new ImageWorkflowSelection("made_up_workflow", null, "Guessing."));

        assertThat(resolved.toolName()).isEqualTo(DEFAULT_WORKFLOW);
        assertThat(resolved.rationale()).contains("made_up_workflow");
    }

    /**
     * The default may not be among the candidates, for example when the intent narrowed them to a
     * person edit, so the first candidate is used instead.
     */
    @Test
    void apply_defaultIsNotACandidate_usesTheFirstCandidate() throws Exception {
        ImageWorkflowSelection resolved = resolve(List.of(PERSON_REFERENCE),
                new ImageWorkflowSelection(null, null, "The routing model failed."));

        assertThat(resolved.toolName()).isEqualTo("person_reference_image");
    }

    @Test
    void apply_noSelectionOnTheState_fallsBackRatherThanFailing() throws Exception {
        ImageWorkflowSelection resolved = selection(resolveImageWorkflowNode.apply(
                new ImageWorkflowState(stateData(List.of(GENERATE_IMAGE)))).get());

        assertThat(resolved.toolName()).isEqualTo(DEFAULT_WORKFLOW);
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
