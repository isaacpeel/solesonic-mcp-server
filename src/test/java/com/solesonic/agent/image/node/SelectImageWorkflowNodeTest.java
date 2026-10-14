package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageIntent;
import com.solesonic.agent.image.ImageIntentClassification;
import com.solesonic.agent.image.ImageRoutingProperties;
import com.solesonic.agent.image.ImageWorkflowSelection;
import com.solesonic.agent.image.ImageWorkflowState;
import com.solesonic.agent.image.WorkflowCandidate;
import com.solesonic.agent.image.WorkflowChoice;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.service.comfyui.ComfyWorkflowRegistry;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectImageWorkflowNodeTest {

    private static final String USER_PROMPT = "put me on a sunny beach";
    private static final String DESCRIPTION = "a man with glasses and a beard";
    private static final String INTENT_RATIONALE = "Intent decided.";

    private static final String BASE_WORKFLOW = """
            {"6": {"inputs": {"text": "__PROMPT__"}}}""";

    private static final String ONE_SLOT_VARIANT = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "40": {"inputs": {"image": "__REFERENCE_IMAGE_1__"}}}""";

    private static final Resource TEMPLATE = new ByteArrayResource("""
            {intentGuidance}
            Workflows:
            {workflows}""".getBytes(StandardCharsets.UTF_8));

    private static final ImageRoutingProperties ROUTING = new ImageRoutingProperties("generate_image", Map.of(
            ImageIntent.NEW_IMAGE, List.of("generate_image", "generate_special_image"),
            ImageIntent.RESTYLE_PHOTO, List.of("generate_image", "generate_special_image"),
            ImageIntent.EDIT_PERSON_IN_PHOTO, List.of("person_reference_image")));

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ComfyWorkflowRegistry comfyWorkflowRegistry;

    private SelectImageWorkflowNode selectImageWorkflowNode;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        ComfyWorkflowTemplate baseTemplate = ComfyWorkflowTemplate.parse(BASE_WORKFLOW, jsonMapper);
        ComfyWorkflowTemplate oneSlotTemplate = ComfyWorkflowTemplate.parse(ONE_SLOT_VARIANT, jsonMapper);

        //Lenient: the missing-prompt test fails before the registry is consulted.
        lenient().when(comfyWorkflowRegistry.registeredWorkflows()).thenReturn(List.of(
                new RegisteredComfyWorkflow("generate_image", "FLUX", "General purpose.", baseTemplate, oneSlotTemplate),
                new RegisteredComfyWorkflow("generate_special_image", "Special", "Photoreal people.", baseTemplate, oneSlotTemplate),
                new RegisteredComfyWorkflow("person_reference_image", "Kontext", "Edits a person.", baseTemplate, oneSlotTemplate),
                new RegisteredComfyWorkflow("text_only", "Text only", "No reference images.", baseTemplate)));

        selectImageWorkflowNode = new SelectImageWorkflowNode(chatClient, comfyWorkflowRegistry, ROUTING, TEMPLATE);
    }

    /**
     * The case that motivated the intent step: a person edit maps to one workflow, so it is chosen
     * deterministically and the model never gets to pick the general workflow instead.
     */
    @Test
    void apply_personEditIntent_selectsTheOnlyMappedWorkflowWithoutAskingTheModel() throws Exception {
        Map<String, Object> result = selectImageWorkflowNode.apply(
                state(1, ImageIntent.EDIT_PERSON_IN_PHOTO)).get();

        assertThat(candidateToolNames(result)).containsExactly("person_reference_image");
        assertThat(selection(result).toolName()).isEqualTo("person_reference_image");
        assertThat(selection(result).enhancedPrompt()).isNull();
        assertThat(selection(result).rationale()).contains(INTENT_RATIONALE);
        verifyNoInteractions(chatClient);
    }

    @Test
    void apply_restyleIntent_asksTheModelToChooseAmongTheMappedWorkflowsWithTheDescriptions() throws Exception {
        stubChatClientReturning(new WorkflowChoice("generate_special_image", "Photoreal."));

        Map<String, Object> result = selectImageWorkflowNode.apply(state(1, ImageIntent.RESTYLE_PHOTO)).get();

        assertThat(candidateToolNames(result)).containsExactly("generate_image", "generate_special_image");
        assertThat(selection(result).toolName()).isEqualTo("generate_special_image");
        assertThat(selection(result).rationale()).isEqualTo(INTENT_RATIONALE + " Photoreal.");

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientRequestSpec).system(systemCaptor.capture());
        assertThat(systemCaptor.getValue())
                .contains("restyle the attached photo", "generate_image", "generate_special_image")
                .doesNotContain("person_reference_image");

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientRequestSpec).user(userCaptor.capture());
        assertThat(userCaptor.getValue()).contains("[1 photo attached]", "Photo 1: " + DESCRIPTION, USER_PROMPT);
    }

    @Test
    void apply_newImageWithoutPhotos_offersOnlyTheMappedWorkflows() throws Exception {
        stubChatClientReturning(new WorkflowChoice("generate_image", "General."));

        Map<String, Object> result = selectImageWorkflowNode.apply(state(0, ImageIntent.NEW_IMAGE)).get();

        assertThat(candidateToolNames(result)).containsExactly("generate_image", "generate_special_image");
    }

    @Test
    void apply_undecidedIntent_offersEveryWorkflowThatAcceptsTheImages() throws Exception {
        stubChatClientReturning(new WorkflowChoice("person_reference_image", "Person."));

        Map<String, Object> result = selectImageWorkflowNode.apply(state(1, null)).get();

        assertThat(candidateToolNames(result))
                .containsExactly("generate_image", "generate_special_image", "person_reference_image");
    }

    @Test
    void apply_noMappedWorkflowAcceptsTheImages_offersEveryWorkflowThatDoes() throws Exception {
        ImageRoutingProperties routing = new ImageRoutingProperties("generate_image",
                Map.of(ImageIntent.EDIT_PERSON_IN_PHOTO, List.of("retired_workflow")));
        stubChatClientReturning(new WorkflowChoice("generate_image", "General."));

        Map<String, Object> result = new SelectImageWorkflowNode(chatClient, comfyWorkflowRegistry, routing, TEMPLATE)
                .apply(state(1, ImageIntent.EDIT_PERSON_IN_PHOTO)).get();

        assertThat(candidateToolNames(result))
                .containsExactly("generate_image", "generate_special_image", "person_reference_image");
    }

    @Test
    void apply_noWorkflowAcceptsTheImages_reportsNoCandidatesWithoutAskingTheModel() throws Exception {
        Map<String, Object> result = selectImageWorkflowNode.apply(state(2, ImageIntent.RESTYLE_PHOTO)).get();

        assertThat(candidateToolNames(result)).isEmpty();
        assertThat(selection(result).toolName()).isNull();
        verifyNoInteractions(chatClient);
    }

    @Test
    void apply_modelThrows_leavesTheWorkflowUnchosenInsteadOfFailing() throws Exception {
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.system(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenThrow(new IllegalStateException("model offline"));

        CompletableFuture<Map<String, Object>> future = selectImageWorkflowNode.apply(state(1, ImageIntent.RESTYLE_PHOTO));

        assertThat(future).isCompleted();
        assertThat(selection(future.get()).toolName()).isNull();
        assertThat(selection(future.get()).rationale()).contains("routing model failed");
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

    private void stubChatClientReturning(WorkflowChoice workflowChoice) {
        ResponseEntity<ChatResponse, WorkflowChoice> responseEntity = new ResponseEntity<>(null, workflowChoice);

        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.system(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.responseEntity(WorkflowChoice.class)).thenReturn(responseEntity);
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
