package com.solesonic.mcp.service.comfyui;

import com.solesonic.agent.image.ImageIntent;
import com.solesonic.model.comfyui.ComfyWorkflow;
import com.solesonic.model.comfyui.ComfyWorkflowReferenceVariant;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.repository.comfyui.ComfyWorkflowRepository;
import com.solesonic.service.comfyui.ComfyWorkflowRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_HEIGHT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_SEED;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_WIDTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComfyWorkflowRegistryTest {

    private static final String VALID_WORKFLOW = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "31": {"inputs": {"seed": "__SEED__", "steps": 4}},
             "27": {"inputs": {"width": "__WIDTH__", "height": "__HEIGHT__"}}}""";

    private static final String PROMPT_ONLY_WORKFLOW = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "27": {"inputs": {"width": 1024, "height": 1024}}}""";

    private static final String ONE_SLOT_VARIANT = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "31": {"inputs": {"seed": "__SEED__", "denoise": 0.6}},
             "40": {"class_type": "LoadImage", "inputs": {"image": "__REFERENCE_IMAGE_1__"}}}""";

    private static final String TWO_SLOT_VARIANT = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "40": {"class_type": "LoadImage", "inputs": {"image": "__REFERENCE_IMAGE_1__"}},
             "41": {"class_type": "LoadImage", "inputs": {"image": "__REFERENCE_IMAGE_2__"}}}""";

    private static final String THREE_SLOT_VARIANT = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "40": {"class_type": "LoadImage", "inputs": {"image": "__REFERENCE_IMAGE_1__"}},
             "41": {"class_type": "LoadImage", "inputs": {"image": "__REFERENCE_IMAGE_2__"}},
             "42": {"class_type": "LoadImage", "inputs": {"image": "__REFERENCE_IMAGE_3__"}}}""";

    @Mock
    private ComfyWorkflowRepository comfyWorkflowRepository;

    private ComfyWorkflowRegistry comfyWorkflowRegistry;

    @BeforeEach
    void setUp() {
        comfyWorkflowRegistry = new ComfyWorkflowRegistry(comfyWorkflowRepository, JsonMapper.builder().build());
    }

    @Test
    void loadWorkflows_parsesEnabledRowsAndCarriesRowIdentityThrough() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc())
                .thenReturn(List.of(row("generate_image_flux_schnell", "FLUX.1-schnell", VALID_WORKFLOW)));

        comfyWorkflowRegistry.loadWorkflows();

        assertThat(comfyWorkflowRegistry.registeredWorkflows()).singleElement().satisfies(registered -> {
            assertThat(registered.toolName()).isEqualTo("generate_image_flux_schnell");
            assertThat(registered.name()).isEqualTo("FLUX.1-schnell");
            assertThat(registered.template().hasToken(TOKEN_SEED)).isTrue();
        });
    }

    /**
     * The tokens a row carries drive its tool's input schema, so a workflow that pins its own
     * resolution must not advertise width and height.
     */
    @Test
    void loadWorkflows_reportsOnlyTheTokensEachRowActuallyCarries() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc())
                .thenReturn(List.of(row("generate_image_fixed", "Fixed size", PROMPT_ONLY_WORKFLOW)));

        comfyWorkflowRegistry.loadWorkflows();

        RegisteredComfyWorkflow registered = comfyWorkflowRegistry.registeredWorkflows().getFirst();
        assertThat(registered.template().hasToken(TOKEN_WIDTH)).isFalse();
        assertThat(registered.template().hasToken(TOKEN_HEIGHT)).isFalse();
        assertThat(registered.template().hasToken(TOKEN_SEED)).isFalse();
    }

    /**
     * Rows are typed in by hand, so one bad paste must not take every unrelated tool down with it.
     */
    @Test
    void loadWorkflows_skipsUnusableRowsAndKeepsTheRest() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc()).thenReturn(List.of(
                row("malformed_json", "Broken", "{\"6\": "),
                row("no_prompt_token", "Promptless", "{\"6\": {\"inputs\": {\"text\": \"hardcoded\"}}}"),
                row("illegal tool name", "Bad name", VALID_WORKFLOW),
                row("generate_image_flux_schnell", "FLUX.1-schnell", VALID_WORKFLOW)));

        comfyWorkflowRegistry.loadWorkflows();

        assertThat(comfyWorkflowRegistry.registeredWorkflows())
                .extracting(RegisteredComfyWorkflow::toolName)
                .containsExactly("generate_image_flux_schnell");
    }

    @Test
    void loadWorkflows_onAnEmptyTableRegistersNothingAndDoesNotThrow() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc()).thenReturn(List.of());

        comfyWorkflowRegistry.loadWorkflows();

        assertThat(comfyWorkflowRegistry.registeredWorkflows()).isEmpty();
    }

    @Test
    void loadWorkflows_registersAValidReferenceVariantUnderItsImageCount() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc())
                .thenReturn(List.of(row("generate_image", "FLUX", VALID_WORKFLOW, variant(1, ONE_SLOT_VARIANT))));

        comfyWorkflowRegistry.loadWorkflows();

        RegisteredComfyWorkflow registered = comfyWorkflowRegistry.registeredWorkflows().getFirst();
        assertThat(registered.referenceVariant(1)).isPresent();
        assertThat(registered.acceptedReferenceImageCounts()).containsExactly(1);
    }

    /**
     * One tool can take one, two or three photos: each count gets its own graph.
     */
    @Test
    void loadWorkflows_registersOneVariantPerImageCount() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc()).thenReturn(List.of(
                row("person_workflow", "Person", VALID_WORKFLOW,
                        variant(3, THREE_SLOT_VARIANT), variant(1, ONE_SLOT_VARIANT), variant(2, TWO_SLOT_VARIANT))));

        comfyWorkflowRegistry.loadWorkflows();

        RegisteredComfyWorkflow registered = comfyWorkflowRegistry.registeredWorkflows().getFirst();
        assertThat(registered.acceptedReferenceImageCounts()).containsExactly(1, 2, 3);
        assertThat(registered.referenceVariant(2)).hasValueSatisfying(template ->
                assertThat(template.referenceImageSlots()).isEqualTo(2));
    }

    @Test
    void loadWorkflows_carriesTheIntentsEachRowServes() {
        ComfyWorkflow servesPersonEdits = row("person_workflow", "Person", VALID_WORKFLOW, variant(1, ONE_SLOT_VARIANT));
        servesPersonEdits.setIntents(Set.of(ImageIntent.EDIT_PERSON_IN_PHOTO));
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc())
                .thenReturn(List.of(servesPersonEdits, row("my_workflow", "My workflow", VALID_WORKFLOW)));

        comfyWorkflowRegistry.loadWorkflows();

        RegisteredComfyWorkflow person = comfyWorkflowRegistry.registeredWorkflows().getFirst();
        RegisteredComfyWorkflow untagged = comfyWorkflowRegistry.registeredWorkflows().getLast();
        assertThat(person.servesIntent(ImageIntent.EDIT_PERSON_IN_PHOTO)).isTrue();
        assertThat(person.servesIntent(ImageIntent.NEW_IMAGE)).isFalse();
        assertThat(person.servesIntent(null)).isFalse();
        assertThat(untagged.intents()).isEmpty();
    }

    @Test
    void loadWorkflows_aRowWithoutAVariantAcceptsNoReferenceImages() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc())
                .thenReturn(List.of(row("generate_image", "FLUX", VALID_WORKFLOW)));

        comfyWorkflowRegistry.loadWorkflows();

        RegisteredComfyWorkflow registered = comfyWorkflowRegistry.registeredWorkflows().getFirst();
        assertThat(registered.acceptedReferenceImageCounts()).isEmpty();
        assertThat(registered.acceptsReferenceImages(1)).isFalse();
    }

    /**
     * A broken variant must not take a working text-only tool down with it.
     */
    @Test
    void loadWorkflows_anInvalidVariantIsDroppedButTheBaseToolStillRegisters() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc()).thenReturn(List.of(
                row("malformed_variant", "Malformed", VALID_WORKFLOW, variant(1, "{\"6\": ")),
                row("variant_without_images", "No images", VALID_WORKFLOW, variant(1, VALID_WORKFLOW)),
                row("variant_with_gap", "Gap", VALID_WORKFLOW, variant(1, """
                        {"6": {"inputs": {"text": "__PROMPT__"}},
                         "8": {"inputs": {"image": "__REFERENCE_IMAGE_2__"}}}"""))));

        comfyWorkflowRegistry.loadWorkflows();

        assertThat(comfyWorkflowRegistry.registeredWorkflows())
                .hasSize(3)
                .allSatisfy(registered -> assertThat(registered.acceptedReferenceImageCounts()).isEmpty());
    }

    /**
     * The attached image count picks the graph, so a graph loading a different number of images than
     * its row says would be handed the wrong number of uploads. Only that variant is dropped.
     */
    @Test
    void loadWorkflows_aVariantWhoseGraphDisagreesWithItsImageCountIsDroppedAndItsSiblingsKept() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc()).thenReturn(List.of(
                row("person_workflow", "Person", VALID_WORKFLOW,
                        variant(1, ONE_SLOT_VARIANT), variant(2, THREE_SLOT_VARIANT))));

        comfyWorkflowRegistry.loadWorkflows();

        RegisteredComfyWorkflow registered = comfyWorkflowRegistry.registeredWorkflows().getFirst();
        assertThat(registered.acceptedReferenceImageCounts()).containsExactly(1);
    }

    @Test
    void loadWorkflows_aDuplicateImageCountRegistersThatCountOnceWithoutFailingTheRow() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc()).thenReturn(List.of(
                row("generate_image", "FLUX", VALID_WORKFLOW,
                        variant(1, ONE_SLOT_VARIANT), variant(1, ONE_SLOT_VARIANT.replace("0.6", "0.4")))));

        comfyWorkflowRegistry.loadWorkflows();

        RegisteredComfyWorkflow registered = comfyWorkflowRegistry.registeredWorkflows().getFirst();
        assertThat(registered.acceptedReferenceImageCounts()).containsExactly(1);
    }

    /**
     * A {@code LoadImage} node with no image fails ComfyUI validation, so a text-only graph carrying
     * a reference token could never succeed on the path that supplies no images.
     */
    @Test
    void loadWorkflows_skipsARowWhoseBaseWorkflowCarriesReferenceTokens() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc())
                .thenReturn(List.of(row("base_needs_images", "Wrong column", ONE_SLOT_VARIANT)));

        comfyWorkflowRegistry.loadWorkflows();

        assertThat(comfyWorkflowRegistry.registeredWorkflows()).isEmpty();
    }

    private static ComfyWorkflowReferenceVariant variant(int imageCount, String workflowJson) {
        return new ComfyWorkflowReferenceVariant(imageCount, workflowJson);
    }

    private ComfyWorkflow row(String toolName, String name, String workflowJson,
                              ComfyWorkflowReferenceVariant... referenceVariants) {
        ComfyWorkflow comfyWorkflow = new ComfyWorkflow();
        comfyWorkflow.setId(UUID.randomUUID());
        comfyWorkflow.setToolName(toolName);
        comfyWorkflow.setName(name);
        comfyWorkflow.setDescription("Generates an image.");
        comfyWorkflow.setWorkflowJson(workflowJson);
        comfyWorkflow.setReferenceVariants(List.of(referenceVariants));
        comfyWorkflow.setEnabled(true);

        return comfyWorkflow;
    }
}
