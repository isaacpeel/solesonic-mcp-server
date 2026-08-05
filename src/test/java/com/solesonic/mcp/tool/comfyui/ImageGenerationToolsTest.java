package com.solesonic.mcp.tool.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.model.comfyui.GeneratedImage;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.service.comfyui.ComfyUiService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageGenerationToolsTest {

    private static final String BASE64_PNG = "iVBORw0KGgo=";

    @Mock
    private ComfyUiService comfyUiService;

    @Mock
    private McpSyncRequestContext mcpSyncRequestContext;

    private ImageGenerationTools imageGenerationTools;

    @BeforeEach
    void setUp() {
        imageGenerationTools = new ImageGenerationTools(comfyUiService);
    }

    @Test
    void generateImage_happyPath_returnsInlineImageContentAndMetadata() {
        when(comfyUiService.generate(any(ImageGenerationRequest.class), any(ProgressReporter.class)))
                .thenReturn(new GeneratedImage(BASE64_PNG, 1024, 1024, 4, 987654321L, 8.2));

        CallToolResult result = imageGenerationTools.generateImage(
                mcpSyncRequestContext, "a lighthouse in a storm");

        assertThat(result.isError()).isFalse();

        ImageContent imageContent = firstImageContent(result);
        assertThat(imageContent.data()).isEqualTo(BASE64_PNG);
        assertThat(imageContent.mimeType()).isEqualTo("image/png");

        assertThat(firstText(result))
                .contains("1024x1024")
                .contains("987654321");
    }

    @Test
    void generateImage_alwaysRequests1024SquareAtFourSteps() {
        when(comfyUiService.generate(any(ImageGenerationRequest.class), any(ProgressReporter.class)))
                .thenReturn(new GeneratedImage(BASE64_PNG, 1024, 1024, 4, 1L, 5.0));

        imageGenerationTools.generateImage(mcpSyncRequestContext, "a lighthouse");

        ImageGenerationRequest request = capturedRequest();
        assertThat(request.prompt()).isEqualTo("a lighthouse");
        assertThat(request.width()).isEqualTo(1024);
        assertThat(request.height()).isEqualTo(1024);
        assertThat(request.steps()).isEqualTo(4);
    }

    @Test
    void generateImage_blankPrompt_isRejectedBeforeAnyHttpCall() {
        CallToolResult result = imageGenerationTools.generateImage(mcpSyncRequestContext, "   ");

        assertThat(result.isError()).isTrue();
        assertThat(firstText(result)).contains("non-empty prompt");
        verifyNoInteractions(comfyUiService);
    }

    @Test
    void generateImage_nullPrompt_isRejectedBeforeAnyHttpCall() {
        CallToolResult result = imageGenerationTools.generateImage(mcpSyncRequestContext, null);

        assertThat(result.isError()).isTrue();
        assertThat(firstText(result)).contains("non-empty prompt");
        verify(comfyUiService, never()).generate(any(), any());
    }

    @Test
    void generateImage_resolvesANonNegativeRandomSeedAndReportsIt() {
        when(comfyUiService.generate(any(ImageGenerationRequest.class), any(ProgressReporter.class)))
                .thenAnswer(invocation -> {
                    ImageGenerationRequest request = invocation.getArgument(0);
                    return new GeneratedImage(BASE64_PNG, 1024, 1024, 4, request.seed(), 7.0);
                });

        CallToolResult result = imageGenerationTools.generateImage(mcpSyncRequestContext, "a lighthouse");

        long resolvedSeed = capturedRequest().seed();
        assertThat(resolvedSeed).isNotNegative();
        assertThat(firstText(result)).contains(Long.toString(resolvedSeed));
    }

    @Test
    void generateImage_usesAFreshSeedOnEveryCall() {
        when(comfyUiService.generate(any(ImageGenerationRequest.class), any(ProgressReporter.class)))
                .thenReturn(new GeneratedImage(BASE64_PNG, 1024, 1024, 4, 1L, 5.0));

        imageGenerationTools.generateImage(mcpSyncRequestContext, "a lighthouse");
        imageGenerationTools.generateImage(mcpSyncRequestContext, "a lighthouse");

        ArgumentCaptor<ImageGenerationRequest> captor = ArgumentCaptor.forClass(ImageGenerationRequest.class);
        verify(comfyUiService, times(2)).generate(captor.capture(), any(ProgressReporter.class));

        assertThat(captor.getAllValues().getFirst().seed())
                .isNotEqualTo(captor.getAllValues().getLast().seed());
    }

    private ImageGenerationRequest capturedRequest() {
        ArgumentCaptor<ImageGenerationRequest> captor = ArgumentCaptor.forClass(ImageGenerationRequest.class);
        verify(comfyUiService).generate(captor.capture(), any(ProgressReporter.class));

        return captor.getValue();
    }

    private ImageContent firstImageContent(CallToolResult result) {
        return result.content().stream()
                .filter(ImageContent.class::isInstance)
                .map(ImageContent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CallToolResult carried no ImageContent"));
    }

    private String firstText(CallToolResult result) {
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CallToolResult carried no TextContent"));
    }
}
