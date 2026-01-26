package com.solesonic.mcp.tool.comfyui;

import com.solesonic.mcp.model.comfyui.ComfyWorkflowResponse;
import com.solesonic.mcp.service.comfyui.ComfyUiService;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

@SuppressWarnings("unused")
@Service
public class ComfyUiTools {

    private static final Logger log = LoggerFactory.getLogger(ComfyUiTools.class);

    public static final String GENERATE_IMAGE = "generate_image";
    public static final String GET_IMAGE_JOB_STATUS = "get_image_job_status";
    public static final String GET_GENERATED_IMAGE = "get_generated_image";

    public static final String GENERATE_IMAGE_DESC = """
            Starts an asynchronous image generation job using a ComfyUI workflow based on the provided prompt.
            Returns job metadata including jobId, clientId, promptId, and initial status.
            Progress and completion are communicated via notifications.
            """;

    public static final String GET_IMAGE_JOB_STATUS_DESC = """
            Gets the current status of an image generation job.
            Returns the job status including state, message, progress percentage, and current node.
            """;

    public static final String GET_GENERATED_IMAGE_DESC = """
            Retrieves the generated image for a completed job.
            Returns the image resource if available.
            """;

    private final ComfyUiService comfyUiService;

    public ComfyUiTools(ComfyUiService comfyUiService) {
        this.comfyUiService = comfyUiService;
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-GENERATE-IMAGE')")
    @McpTool(name = GENERATE_IMAGE, description = GENERATE_IMAGE_DESC)
    public ComfyWorkflowResponse generateImage(
            McpSyncRequestContext mcpContext,
            @McpToolParam(description = "The text prompt describing the image you want to generate. Be descriptive for better results.")
            String imagePrompt
    ) {
        log.info("Starting async image generation using a ComfyUI workflow");
        UUID clientId = UUID.randomUUID();

        return comfyUiService.generateImage(imagePrompt,  clientId);
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-GENERATE-IMAGE')")
    @McpResource(name = GET_GENERATED_IMAGE, description =  GET_GENERATED_IMAGE_DESC, uri = "image://images/{promptId}", mimeType = "image/png")
    public McpSchema.BlobResourceContents getGeneratedImage(
            @McpToolParam(description = "The prompt ID returned from generate_image")
            String promptId
    ) {
        log.info("Getting generated image for prompt: {}", promptId);

        Resource image = comfyUiService.viewImageByPromptId(promptId);
        String filename = image.getFilename();
        byte[] bytes;
        try {
            bytes = image.getContentAsByteArray();
            String encoded = Base64.getEncoder().encodeToString(bytes);
            return new McpSchema.BlobResourceContents("image:/images/"+promptId, "image/png", encoded);
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }
}
