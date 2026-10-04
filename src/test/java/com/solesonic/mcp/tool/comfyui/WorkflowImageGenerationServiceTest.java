package com.solesonic.mcp.tool.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.model.comfyui.GeneratedImage;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.model.comfyui.ReferenceImage;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.service.comfyui.ComfyUiService;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowImageGenerationServiceTest {

    private static final String BASE_WORKFLOW = """
            {"6": {"inputs": {"text": "__PROMPT__"}}}""";

    private static final String TWO_SLOT_VARIANT = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "40": {"inputs": {"image": "__REFERENCE_IMAGE_1__"}},
             "41": {"inputs": {"image": "__REFERENCE_IMAGE_2__"}}}""";

    private static final ReferenceImage PNG = new ReferenceImage(new byte[]{1}, "image/png");

    @Mock
    private ComfyUiService comfyUiService;

    private final ProgressReporter progressReporter = new ProgressReporter((_, _) -> {
    });

    private WorkflowImageGenerationService workflowImageGenerationService;
    private ComfyWorkflowTemplate baseTemplate;
    private ComfyWorkflowTemplate variantTemplate;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        baseTemplate = ComfyWorkflowTemplate.parse(BASE_WORKFLOW, jsonMapper);
        variantTemplate = ComfyWorkflowTemplate.parse(TWO_SLOT_VARIANT, jsonMapper);
        workflowImageGenerationService = new WorkflowImageGenerationService(comfyUiService,
                new ReferenceImageArguments(DataSize.ofMegabytes(1)));
    }

    @Test
    void generate_withAnInvalidRawArgument_isAnErrorResultBeforeComfyUiIsCalled() {
        CallToolResult callToolResult = workflowImageGenerationService.generate(withVariant(), request(List.of()),
                List.of(Map.of("data", "not*base64!", "mimeType", "image/png")), progressReporter);

        assertThat(callToolResult.isError()).isTrue();
        assertThat(text(callToolResult)).contains("not valid base64");
        verifyNoInteractions(comfyUiService);
    }

    @Test
    void generate_withARawArgument_decodesItAndUsesTheVariant() {
        byte[] pngBytes = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        String data = Base64.getEncoder().encodeToString(pngBytes);
        when(comfyUiService.generate(same(variantTemplate), any(), any()))
                .thenAnswer(invocation -> generatedImage(invocation.getArgument(1)));

        CallToolResult callToolResult = workflowImageGenerationService.generate(withVariant(), request(List.of()),
                List.of(Map.of("data", data, "mimeType", "image/png"), Map.of("data", data, "mimeType", "image/png")),
                progressReporter);

        assertThat(text(callToolResult)).contains("Reference images: 2");
    }

    @Test
    void generate_withoutReferenceImages_usesTheTextOnlyGraph() {
        ImageGenerationRequest request = request(List.of());
        when(comfyUiService.generate(same(baseTemplate), eq(request), any())).thenReturn(generatedImage(request));

        CallToolResult callToolResult = workflowImageGenerationService.generate(withVariant(), request, progressReporter);

        assertThat(callToolResult.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(callToolResult)).doesNotContain("Reference images");
    }

    @Test
    void generate_withTheRightNumberOfImages_usesTheVariantAndReportsTheCount() {
        ImageGenerationRequest request = request(List.of(PNG, PNG));
        when(comfyUiService.generate(same(variantTemplate), eq(request), any())).thenReturn(generatedImage(request));

        CallToolResult callToolResult = workflowImageGenerationService.generate(withVariant(), request, progressReporter);

        verify(comfyUiService).generate(same(variantTemplate), eq(request), any());
        assertThat(text(callToolResult)).contains("Reference images: 2");
    }

    @Test
    void generate_withTheWrongNumberOfImages_isRejectedBeforeComfyUiIsCalled() {
        CallToolResult callToolResult = workflowImageGenerationService.generate(
                withVariant(), request(List.of(PNG)), progressReporter);

        assertThat(callToolResult.isError()).isTrue();
        assertThat(text(callToolResult)).contains("exactly 2 reference images; 1 supplied");
        verifyNoInteractions(comfyUiService);
    }

    @Test
    void generate_withImagesOnARowWithoutAVariant_isRejected() {
        RegisteredComfyWorkflow textOnly = new RegisteredComfyWorkflow("generate_image", "FLUX", "desc", baseTemplate);

        CallToolResult callToolResult = workflowImageGenerationService.generate(
                textOnly, request(List.of(PNG)), progressReporter);

        assertThat(callToolResult.isError()).isTrue();
        assertThat(text(callToolResult)).contains("does not accept reference images");
        verifyNoInteractions(comfyUiService);
    }

    private RegisteredComfyWorkflow withVariant() {
        return new RegisteredComfyWorkflow("generate_image", "FLUX", "desc", baseTemplate, variantTemplate);
    }

    private ImageGenerationRequest request(List<ReferenceImage> referenceImages) {
        return new ImageGenerationRequest("a watercolor", 1024, 1024, 42L, referenceImages);
    }

    private GeneratedImage generatedImage(ImageGenerationRequest request) {
        return GeneratedImage.imageGenerationRequest(request).base64Png("aW1hZ2U=").elapsedSeconds(1.0).build();
    }

    private String text(CallToolResult callToolResult) {
        return callToolResult.content().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .collect(Collectors.joining("\n"));
    }
}
