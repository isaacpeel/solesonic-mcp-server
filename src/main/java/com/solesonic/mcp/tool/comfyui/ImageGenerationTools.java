package com.solesonic.mcp.tool.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.model.comfyui.GeneratedImage;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.service.comfyui.ComfyUiService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MIME_TYPE_PNG;

@SuppressWarnings("unused")
@Service
public class ImageGenerationTools {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationTools.class);

    public static final String GENERATE_IMAGE = "generate_image";

    public static final String GENERATE_IMAGE_DESC = """
            Creates a brand new image from a natural-language description and returns it inline as a PNG.
            Use this whenever the user asks for a picture, illustration, artwork, diagram concept, logo
            idea, mockup, or any other visual to be made from a written description.
            This tool only creates images. It cannot edit, resize, or annotate an existing image, and it
            cannot search for or retrieve images that already exist.
            """;

    public static final String PROMPT_DESC = """
            Describe the image to generate. Include subject, style, lighting, and composition.
            Longer, more specific prompts produce better results.
            """;

    private final ComfyUiService comfyUiService;

    public ImageGenerationTools(ComfyUiService comfyUiService) {
        this.comfyUiService = comfyUiService;
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-GENERATE-IMAGE')")
    @McpTool(name = GENERATE_IMAGE, description = GENERATE_IMAGE_DESC)
    public CallToolResult generateImage(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = PROMPT_DESC)
            String prompt
    ) {
        if (StringUtils.isEmpty(prompt)) {
            return CallToolResult.builder()
                    .addTextContent("A non-empty prompt is required to generate an image.")
                    .isError(true)
                    .build();
        }

        ImageGenerationRequest imageGenerationRequest = new ImageGenerationRequest(prompt);

        log.info("Generating image at {}x{} with {} steps, seed {}",
                imageGenerationRequest.width(),
                imageGenerationRequest.height(),
                imageGenerationRequest.steps(),
                imageGenerationRequest.seed());

        ProgressReporter progressReporter = new ProgressReporter(mcpSyncRequestContext);

        GeneratedImage generatedImage = comfyUiService.generate(imageGenerationRequest, progressReporter);

        ImageContent imageContent = ImageContent.builder(generatedImage.base64Png(), MIME_TYPE_PNG)
                .build();

        return CallToolResult.builder()
                .addContent(imageContent)
                .addTextContent(metadata(generatedImage))
                .build();
    }

    /**
     * The seed is reported for traceability — it identifies the run in ComfyUI's history — but the
     * tool takes no seed parameter, so it is not a knob the caller can turn.
     */
    private String metadata(GeneratedImage generatedImage) {
        return """
                Generated with FLUX.1-schnell.
                Size: %dx%d
                Steps: %d
                Seed: %d
                Elapsed: %.1fs"""
                .formatted(
                        generatedImage.width(),
                        generatedImage.height(),
                        generatedImage.steps(),
                        generatedImage.seed(),
                        generatedImage.elapsedSeconds());
    }
}
