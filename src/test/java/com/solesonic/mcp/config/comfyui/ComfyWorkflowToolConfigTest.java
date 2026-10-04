package com.solesonic.mcp.config.comfyui;

import com.solesonic.mcp.tool.comfyui.WorkflowImageGenerationService;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.service.comfyui.ComfyWorkflowRegistry;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComfyWorkflowToolConfigTest {

    private static final String BASE_WORKFLOW = """
            {"6": {"inputs": {"text": "__PROMPT__"}}}""";

    private static final String TWO_SLOT_VARIANT = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "40": {"inputs": {"image": "__REFERENCE_IMAGE_1__"}},
             "41": {"inputs": {"image": "__REFERENCE_IMAGE_2__"}}}""";

    @Mock
    private ComfyWorkflowRegistry comfyWorkflowRegistry;

    @Mock
    private WorkflowImageGenerationService workflowImageGenerationService;

    @Mock
    private McpSyncServerExchange mcpSyncServerExchange;

    private ComfyWorkflowTemplate baseTemplate;
    private ComfyWorkflowTemplate variantTemplate;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        baseTemplate = ComfyWorkflowTemplate.parse(BASE_WORKFLOW, jsonMapper);
        variantTemplate = ComfyWorkflowTemplate.parse(TWO_SLOT_VARIANT, jsonMapper);
    }

    /**
     * The tag is how a client finds image tools; a row's name is free text and proves nothing.
     */
    @Test
    void tool_isTaggedAsAnImageGenerationToolWhateverItIsNamed() {
        SyncToolSpecification toolSpecification = onlyTool(
                new RegisteredComfyWorkflow("flux_edit", "FLUX edit", "desc", baseTemplate));

        assertThat(toolSpecification.tool().meta()).containsEntry("solesonic/kind", "image-generation");
    }

    @Test
    void inputSchema_aRowWithoutAVariantDoesNotAdvertiseReferenceImages() {
        Map<String, Object> properties = properties(onlyTool(
                new RegisteredComfyWorkflow("generate_image", "FLUX", "desc", baseTemplate)));

        assertThat(properties).containsOnlyKeys("prompt");
    }

    /**
     * {@code minItems} and {@code maxItems} both equal the slot count, so a client can read the exact
     * number the workflow takes rather than parsing it out of prose.
     */
    @Test
    void inputSchema_aRowWithAVariantAdvertisesExactlyItsSlotCount() {
        Map<String, Object> properties = properties(onlyTool(
                new RegisteredComfyWorkflow("generate_image", "FLUX", "desc", baseTemplate, variantTemplate)));

        assertThat(properties).containsKeys("prompt", "reference_images");

        Map<String, Object> referenceImages = map(properties.get("reference_images"));
        assertThat(referenceImages).containsEntry("type", "array")
                .containsEntry("minItems", 2)
                .containsEntry("maxItems", 2);
        assertThat((String) referenceImages.get("description")).contains("exactly 2");

        Map<String, Object> items = map(referenceImages.get("items"));
        assertThat(map(items.get("properties"))).containsOnlyKeys("data", "mimeType");
        assertThat(items.get("required")).isEqualTo(List.of("data", "mimeType"));
    }

    /**
     * The handler never decodes the images itself: decoding sits behind the service's role check,
     * so the raw value is passed through untouched.
     */
    @Test
    void handle_passesTheRawReferenceImagesArgumentToTheAuthorizedService() {
        SyncToolSpecification toolSpecification = onlyTool(
                new RegisteredComfyWorkflow("generate_image", "FLUX", "desc", baseTemplate, variantTemplate));

        List<Map<String, String>> referenceImages = List.of(Map.of("data", "not*base64!", "mimeType", "image/png"));
        CallToolResult expected = CallToolResult.builder().addTextContent("done").build();
        when(workflowImageGenerationService.generate(any(), any(), eq(referenceImages), any())).thenReturn(expected);

        CallToolRequest callToolRequest = CallToolRequest.builder("generate_image")
                .arguments(Map.of("prompt", "a watercolor", "reference_images", referenceImages))
                .build();

        CallToolResult callToolResult = toolSpecification.callHandler().apply(mcpSyncServerExchange, callToolRequest);

        assertThat(callToolResult).isSameAs(expected);
        verify(workflowImageGenerationService).generate(any(), any(), eq(referenceImages), any());
    }

    private SyncToolSpecification onlyTool(RegisteredComfyWorkflow registeredComfyWorkflow) {
        when(comfyWorkflowRegistry.registeredWorkflows()).thenReturn(List.of(registeredComfyWorkflow));

        List<SyncToolSpecification> toolSpecifications = new ComfyWorkflowToolConfig().comfyWorkflowToolSpecifications(
                comfyWorkflowRegistry, workflowImageGenerationService);

        return toolSpecifications.getFirst();
    }

    private Map<String, Object> properties(SyncToolSpecification toolSpecification) {
        return map(toolSpecification.tool().inputSchema().get("properties"));
    }

    private Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);

        Map<String, Object> typed = new LinkedHashMap<>();
        ((Map<?, ?>) value).forEach((key, entry) -> typed.put(String.valueOf(key), entry));

        return typed;
    }
}
