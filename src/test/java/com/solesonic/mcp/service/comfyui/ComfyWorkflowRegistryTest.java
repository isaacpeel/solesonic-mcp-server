package com.solesonic.mcp.service.comfyui;

import com.solesonic.model.comfyui.ComfyWorkflow;
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
    void loadWorkflows_registersAValidReferenceVariantWithItsSlotCount() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc())
                .thenReturn(List.of(row("generate_image", "FLUX", VALID_WORKFLOW, ONE_SLOT_VARIANT)));

        comfyWorkflowRegistry.loadWorkflows();

        RegisteredComfyWorkflow registered = comfyWorkflowRegistry.registeredWorkflows().getFirst();
        assertThat(registered.referenceVariant()).isPresent();
        assertThat(registered.referenceImageSlots()).isEqualTo(1);
    }

    @Test
    void loadWorkflows_aRowWithoutAVariantAcceptsNoReferenceImages() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc())
                .thenReturn(List.of(row("generate_image", "FLUX", VALID_WORKFLOW, null)));

        comfyWorkflowRegistry.loadWorkflows();

        RegisteredComfyWorkflow registered = comfyWorkflowRegistry.registeredWorkflows().getFirst();
        assertThat(registered.referenceVariant()).isEmpty();
        assertThat(registered.referenceImageSlots()).isZero();
    }

    /**
     * A broken variant must not take a working text-only tool down with it.
     */
    @Test
    void loadWorkflows_anInvalidVariantIsDroppedButTheBaseToolStillRegisters() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc()).thenReturn(List.of(
                row("malformed_variant", "Malformed", VALID_WORKFLOW, "{\"6\": "),
                row("variant_without_images", "No images", VALID_WORKFLOW, VALID_WORKFLOW),
                row("variant_with_gap", "Gap", VALID_WORKFLOW, """
                        {"6": {"inputs": {"text": "__PROMPT__"}},
                         "8": {"inputs": {"image": "__REFERENCE_IMAGE_2__"}}}""")));

        comfyWorkflowRegistry.loadWorkflows();

        assertThat(comfyWorkflowRegistry.registeredWorkflows())
                .hasSize(3)
                .allSatisfy(registered -> assertThat(registered.referenceVariant()).isEmpty());
    }

    /**
     * A {@code LoadImage} node with no image fails ComfyUI validation, so a text-only graph carrying
     * a reference token could never succeed on the path that supplies no images.
     */
    @Test
    void loadWorkflows_skipsARowWhoseBaseWorkflowCarriesReferenceTokens() {
        when(comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc())
                .thenReturn(List.of(row("base_needs_images", "Wrong column", ONE_SLOT_VARIANT, null)));

        comfyWorkflowRegistry.loadWorkflows();

        assertThat(comfyWorkflowRegistry.registeredWorkflows()).isEmpty();
    }

    private ComfyWorkflow row(String toolName, String name, String workflowJson, String referenceWorkflowJson) {
        ComfyWorkflow comfyWorkflow = row(toolName, name, workflowJson);
        comfyWorkflow.setReferenceWorkflowJson(referenceWorkflowJson);

        return comfyWorkflow;
    }

    private ComfyWorkflow row(String toolName, String name, String workflowJson) {
        ComfyWorkflow comfyWorkflow = new ComfyWorkflow();
        comfyWorkflow.setId(UUID.randomUUID());
        comfyWorkflow.setToolName(toolName);
        comfyWorkflow.setName(name);
        comfyWorkflow.setDescription("Generates an image.");
        comfyWorkflow.setWorkflowJson(workflowJson);
        comfyWorkflow.setEnabled(true);

        return comfyWorkflow;
    }
}
