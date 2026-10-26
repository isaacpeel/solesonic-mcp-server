package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageIntent;
import com.solesonic.agent.image.ImageIntentClassification;
import com.solesonic.agent.image.ImageWorkflowSelection;
import com.solesonic.agent.image.ImageWorkflowState;
import com.solesonic.agent.image.WorkflowCandidate;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.service.comfyui.ComfyWorkflowRegistry;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectImageWorkflowNodeTest {

    private static final String USER_PROMPT = "put me on a sunny beach";
    private static final String DESCRIPTION = "a man with glasses and a beard";
    private static final String INTENT_RATIONALE = "Intent decided.";
    private static final double THRESHOLD = 0.5;

    private static final String BASE_WORKFLOW = """
            {"6": {"inputs": {"text": "__PROMPT__"}}}""";

    private static final String ONE_SLOT_VARIANT = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "40": {"inputs": {"image": "__REFERENCE_IMAGE_1__"}}}""";

    private static final String TWO_SLOT_VARIANT = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "40": {"inputs": {"image": "__REFERENCE_IMAGE_1__"}},
             "41": {"inputs": {"image": "__REFERENCE_IMAGE_2__"}}}""";

    private static final Set<ImageIntent> NEW_OR_RESTYLE = Set.of(ImageIntent.NEW_IMAGE, ImageIntent.RESTYLE_PHOTO);

    @Mock
    private TypeSafeClient typeSafeClient;

    @Mock
    private ComfyWorkflowRegistry comfyWorkflowRegistry;

    private SelectImageWorkflowNode selectImageWorkflowNode;
    private ComfyWorkflowTemplate baseTemplate;
    private ComfyWorkflowTemplate oneSlotTemplate;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        baseTemplate = ComfyWorkflowTemplate.parse(BASE_WORKFLOW, jsonMapper);
        oneSlotTemplate = ComfyWorkflowTemplate.parse(ONE_SLOT_VARIANT, jsonMapper);
        ComfyWorkflowTemplate twoSlotTemplate = ComfyWorkflowTemplate.parse(TWO_SLOT_VARIANT, jsonMapper);

        //Lenient: the missing-prompt test fails before the registry is consulted.
        lenient().when(comfyWorkflowRegistry.registeredWorkflows()).thenReturn(List.of(
                new RegisteredComfyWorkflow("general_workflow", "General", "General purpose.", baseTemplate,
                        Map.of(1, oneSlotTemplate), NEW_OR_RESTYLE),
                new RegisteredComfyWorkflow("photoreal_workflow", "Photoreal", "Photoreal people.", baseTemplate,
                        Map.of(1, oneSlotTemplate), NEW_OR_RESTYLE),
                new RegisteredComfyWorkflow("person_workflow", "Person", "Edits people.", baseTemplate,
                        Map.of(1, oneSlotTemplate, 2, twoSlotTemplate), Set.of(ImageIntent.EDIT_PERSON_IN_PHOTO)),
                new RegisteredComfyWorkflow("text_only", "Text only", "No reference images.", baseTemplate)));

        selectImageWorkflowNode = new SelectImageWorkflowNode(typeSafeClient, comfyWorkflowRegistry, THRESHOLD);
    }

    /**
     * The case that motivated the intent step: one workflow serves person edits, so it is chosen
     * deterministically and the model never gets to pick the general workflow instead.
     */
    @Test
    void apply_personEditIntent_selectsTheOnlyWorkflowServingItWithoutAskingTheModel() throws Exception {
        Map<String, Object> result = selectImageWorkflowNode.apply(
                state(1, ImageIntent.EDIT_PERSON_IN_PHOTO)).get();

        assertThat(candidateToolNames(result)).containsExactly("person_workflow");
        assertThat(selection(result).toolName()).isEqualTo("person_workflow");
        assertThat(selection(result).enhancedPrompt()).isNull();
        assertThat(selection(result).rationale()).contains(INTENT_RATIONALE);
        verifyNoInteractions(typeSafeClient);
    }

    /**
     * The options are exactly the candidates' tool names, so the model cannot answer with a
     * workflow that was not offered, and each carries the workflow's name and description.
     */
    @Test
    void apply_restyleIntent_asksTheModelToChooseAmongTheWorkflowsServingItWithTheDescriptions() throws Exception {
        stubDecisionModelReturning("photoreal_workflow", 0.9);

        Map<String, Object> result = selectImageWorkflowNode.apply(state(1, ImageIntent.RESTYLE_PHOTO)).get();

        assertThat(candidateToolNames(result)).containsExactly("general_workflow", "photoreal_workflow");
        assertThat(selection(result).toolName()).isEqualTo("photoreal_workflow");
        assertThat(selection(result).rationale())
                .startsWith(INTENT_RATIONALE + " ")
                .contains("photoreal_workflow", "0.90");

        SystemOneRequest systemOneRequest = capturedRequest();
        Choice workflowChoice = (Choice) systemOneRequest.questions().get(SelectImageWorkflowNode.WORKFLOW_QUESTION);

        assertThat(workflowChoice.criteria().keySet()).containsExactly("general_workflow", "photoreal_workflow");
        assertThat(workflowChoice.criteria().get("photoreal_workflow").asText()).isEqualTo("Photoreal: Photoreal people.");
        assertThat(workflowChoice.instructions()).isNotNull();
        assertThat(workflowChoice.instructions().asText()).contains("restyle the attached photo");
        assertThat(systemOneRequest.state().asText())
                .contains("[1 photo attached]", "Photo 1: " + DESCRIPTION, USER_PROMPT);
    }

    @Test
    void apply_newImageWithoutPhotos_offersOnlyTheWorkflowsServingIt() throws Exception {
        stubDecisionModelReturning("general_workflow", 0.9);

        Map<String, Object> result = selectImageWorkflowNode.apply(state(0, ImageIntent.NEW_IMAGE)).get();

        assertThat(candidateToolNames(result)).containsExactly("general_workflow", "photoreal_workflow");
    }

    @Test
    void apply_undecidedIntent_offersEveryWorkflowThatAcceptsTheImages() throws Exception {
        stubDecisionModelReturning("person_workflow", 0.9);

        Map<String, Object> result = selectImageWorkflowNode.apply(state(1, null)).get();

        assertThat(candidateToolNames(result))
                .containsExactly("general_workflow", "photoreal_workflow", "person_workflow");
    }

    /**
     * The only workflow serving person edits takes no photo, so every workflow that takes one is offered.
     */
    @Test
    void apply_noWorkflowServingTheIntentAcceptsTheImages_offersEveryWorkflowThatDoes() throws Exception {
        when(comfyWorkflowRegistry.registeredWorkflows()).thenReturn(List.of(
                new RegisteredComfyWorkflow("general_workflow", "General", "General purpose.", baseTemplate,
                        Map.of(1, oneSlotTemplate), NEW_OR_RESTYLE),
                new RegisteredComfyWorkflow("text_only_person", "Text only", "People, no photo.", baseTemplate,
                        Map.of(), Set.of(ImageIntent.EDIT_PERSON_IN_PHOTO))));

        Map<String, Object> result = selectImageWorkflowNode.apply(state(1, ImageIntent.EDIT_PERSON_IN_PHOTO)).get();

        assertThat(candidateToolNames(result)).containsExactly("general_workflow");
    }

    /**
     * A workflow with a graph per image count is a candidate for each count it has a graph for.
     */
    @Test
    void apply_personEditWithTwoPhotos_selectsTheWorkflowWithATwoImageVariant() throws Exception {
        Map<String, Object> result = selectImageWorkflowNode.apply(
                state(2, ImageIntent.EDIT_PERSON_IN_PHOTO)).get();

        assertThat(candidateToolNames(result)).containsExactly("person_workflow");
        assertThat(selection(result).toolName()).isEqualTo("person_workflow");
        verifyNoInteractions(typeSafeClient);
    }

    @Test
    void apply_noWorkflowAcceptsTheImages_reportsNoCandidatesWithoutAskingTheModel() throws Exception {
        Map<String, Object> result = selectImageWorkflowNode.apply(state(3, ImageIntent.RESTYLE_PHOTO)).get();

        assertThat(candidateToolNames(result)).isEmpty();
        assertThat(selection(result).toolName()).isNull();
        verifyNoInteractions(typeSafeClient);
    }

    @Test
    void apply_confidenceExactlyAtTheThreshold_acceptsTheChoice() throws Exception {
        stubDecisionModelReturning("photoreal_workflow", THRESHOLD);

        Map<String, Object> result = selectImageWorkflowNode.apply(state(1, ImageIntent.RESTYLE_PHOTO)).get();

        assertThat(selection(result).toolName()).isEqualTo("photoreal_workflow");
    }

    /**
     * A low-confidence choice leaves the workflow unchosen, so the resolver falls back to the
     * first candidate.
     */
    @Test
    void apply_confidenceBelowTheThreshold_leavesTheWorkflowUnchosen() throws Exception {
        stubDecisionModelReturning("photoreal_workflow", 0.49);

        Map<String, Object> result = selectImageWorkflowNode.apply(state(1, ImageIntent.RESTYLE_PHOTO)).get();

        assertThat(selection(result).toolName()).isNull();
        assertThat(selection(result).rationale()).contains("photoreal_workflow", "0.49");
    }

    @Test
    void apply_answerMissing_leavesTheWorkflowUnchosenInsteadOfFailing() throws Exception {
        when(typeSafeClient.systemOne(any(SystemOneRequest.class)))
                .thenReturn(new SystemOneResponse("test-decision-model", Map.of(), null));

        CompletableFuture<Map<String, Object>> future = selectImageWorkflowNode.apply(state(1, ImageIntent.RESTYLE_PHOTO));

        assertThat(future).isCompleted();
        assertThat(selection(future.get()).toolName()).isNull();
        assertThat(selection(future.get()).rationale()).contains("decision model failed");
    }

    @Test
    void apply_modelThrows_leavesTheWorkflowUnchosenInsteadOfFailing() throws Exception {
        when(typeSafeClient.systemOne(any(SystemOneRequest.class))).thenThrow(new TypeSafeException("model offline"));

        CompletableFuture<Map<String, Object>> future = selectImageWorkflowNode.apply(state(1, ImageIntent.RESTYLE_PHOTO));

        assertThat(future).isCompleted();
        assertThat(selection(future.get()).toolName()).isNull();
        assertThat(selection(future.get()).rationale()).contains("decision model failed");
    }

    @Test
    void apply_stateWithoutAPrompt_failsWithADescriptiveMessage() {
        CompletableFuture<Map<String, Object>> future = selectImageWorkflowNode.apply(
                new ImageWorkflowState(Map.of(ImageWorkflowState.REFERENCE_IMAGE_COUNT, 0)));

        assertThat(future).isCompletedExceptionally();
        assertThat(catchThrowable(future::join))
                .rootCause()
                .hasMessageContaining("Selecting an image workflow failed")
                .hasMessageContaining("carries no prompt");
    }

    private void stubDecisionModelReturning(String toolName, double confidence) {
        Map<String, Answer> answers = Map.of(SelectImageWorkflowNode.WORKFLOW_QUESTION,
                new ChoiceAnswer(toolName, Map.of(toolName, confidence), confidence));

        when(typeSafeClient.systemOne(any(SystemOneRequest.class)))
                .thenReturn(new SystemOneResponse("test-decision-model", answers, null));
    }

    private SystemOneRequest capturedRequest() {
        ArgumentCaptor<SystemOneRequest> requestCaptor = ArgumentCaptor.forClass(SystemOneRequest.class);
        verify(typeSafeClient).systemOne(requestCaptor.capture());
        return requestCaptor.getValue();
    }

    private static ImageWorkflowState state(int referenceImageCount, ImageIntent imageIntent) {
        Map<String, Object> stateData = new HashMap<>();
        stateData.put(ImageWorkflowState.USER_PROMPT, USER_PROMPT);
        stateData.put(ImageWorkflowState.REFERENCE_IMAGE_COUNT, referenceImageCount);
        stateData.put(ImageWorkflowState.REFERENCE_IMAGE_DESCRIPTIONS,
                referenceImageCount == 0 ? List.of() : List.of(DESCRIPTION));
        stateData.put(ImageWorkflowState.IMAGE_INTENT, new ImageIntentClassification(imageIntent, INTENT_RATIONALE));

        return new ImageWorkflowState(stateData);
    }

    private static List<String> candidateToolNames(Map<String, Object> result) {
        return new ImageWorkflowState(result).candidateWorkflows().orElseThrow().stream()
                .map(WorkflowCandidate::toolName)
                .toList();
    }

    private static ImageWorkflowSelection selection(Map<String, Object> result) {
        return new ImageWorkflowState(result).workflowSelection().orElseThrow();
    }
}
