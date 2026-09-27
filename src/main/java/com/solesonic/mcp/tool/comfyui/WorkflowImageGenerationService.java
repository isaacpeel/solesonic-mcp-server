package com.solesonic.mcp.tool.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.model.comfyui.GeneratedImage;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.service.comfyui.ComfyUiService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MIME_TYPE_PNG;

/**
 * The authorization boundary for every database-backed workflow tool.
 *
 * <p>Workflow tools are built as {@code SyncToolSpecification}s rather than {@code @McpTool} methods,
 * and a handler lambda is not a Spring bean method, so {@code @PreAuthorize} cannot be applied to it
 * — the annotation is enforced by the method-security proxy, which only wraps bean methods. Routing
 * every call through this bean is what keeps the role check real. Calling
 * {@link ComfyUiService#generate} directly from a handler would silently bypass authorization with
 * no error anywhere to notice.
 */
@Service
public class WorkflowImageGenerationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowImageGenerationService.class);

    private final ComfyUiService comfyUiService;

    public WorkflowImageGenerationService(ComfyUiService comfyUiService) {
        this.comfyUiService = comfyUiService;
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-GENERATE-IMAGE')")
    public CallToolResult generate(
            RegisteredComfyWorkflow registeredComfyWorkflow,
            ImageGenerationRequest imageGenerationRequest,
            ProgressReporter progressReporter
    ) {
        if (StringUtils.isBlank(imageGenerationRequest.prompt())) {
            return CallToolResult.builder()
                    .addTextContent("A non-empty prompt is required to generate an image.")
                    .isError(true)
                    .build();
        }

        log.info("Generating image with workflow '{}' at {}x{}, seed {}",
                registeredComfyWorkflow.toolName(),
                imageGenerationRequest.width(),
                imageGenerationRequest.height(),
                imageGenerationRequest.seed());

        GeneratedImage generatedImage = comfyUiService.generate(
                registeredComfyWorkflow.template(), imageGenerationRequest, progressReporter);

        ImageContent imageContent = ImageContent.builder(generatedImage.base64Png(), MIME_TYPE_PNG)
                .build();

        return CallToolResult.builder()
                .addContent(imageContent)
                .addTextContent(metadata(registeredComfyWorkflow, generatedImage))
                .build();
    }

    /**
     * The seed is reported for traceability — it identifies the run in ComfyUI's history — but no
     * tool takes a seed parameter, so it is not a knob the caller can turn. Step count and sampler
     * settings are properties of the stored workflow, so the workflow name stands in for them.
     */
    private String metadata(RegisteredComfyWorkflow registeredComfyWorkflow, GeneratedImage generatedImage) {
        return """
                Generated with %s.
                Size: %dx%d
                Seed: %d
                Elapsed: %.1fs"""
                .formatted(
                        registeredComfyWorkflow.name(),
                        generatedImage.width(),
                        generatedImage.height(),
                        generatedImage.seed(),
                        generatedImage.elapsedSeconds());
    }
}
