package com.solesonic.mcp.tool.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.agent.checkpoint.GraphRunner;
import com.solesonic.agent.checkpoint.GraphThread;
import com.solesonic.agent.image.ImageRequestMessage;
import com.solesonic.agent.image.ImageWorkflowGraph;
import com.solesonic.agent.image.ImageWorkflowSelection;
import com.solesonic.agent.image.ImageWorkflowState;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.service.comfyui.ComfyWorkflowRegistry;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MAX_REFERENCE_IMAGES;
import static com.solesonic.mcp.tool.comfyui.ReferenceImageArguments.PARAM_REFERENCE_IMAGES;

/**
 * The one image-generation tool. A small graph ({@link ImageWorkflowGraph}) chooses which stored
 * ComfyUI workflow fits the request and writes the final prompt; this method then runs that workflow.
 *
 * <p>The reference images never enter the graph, whose state is checkpointed to Redis: the graph
 * sees only how many there are. The raw argument from the request is handed to
 * {@link WorkflowImageGenerationService} undecoded, so base64 decoding and validation stay behind
 * that service's role check.
 *
 * <p>Spring AI binds every declared tool parameter, {@code referenceImages} included, through a JSON
 * round trip before the method-security proxy runs, so that copy is made before the role check.
 * Declaring the parameter is what puts it in the tool's input schema; the request body itself is
 * bounded by the reverse proxy in front of the server.
 */
@Service
public class ImageGenerationTools {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationTools.class);

    public static final String GENERATE_IMAGE_AGENT = "generate_image";

    private static final String GENERATE_IMAGE_AGENT_DESCRIPTION = """
            Generates an image from a text description, optionally guided by attached reference images.
            The server chooses the best image workflow for the request and refines the prompt itself.
            Pass the user's request in prompt exactly as they wrote it: do not enhance, expand or
            restyle it, do not choose a style or model, and do not describe the attached images in it.
            If the user attached images for this request, pass them in referenceImages, and put a
            short description of what each image shows in that image's description field.
            """;

    private static final String PROMPT_DESCRIPTION =
            "The user's image request, exactly as they wrote it. The server refines it.";

    private static final String WIDTH_DESCRIPTION =
            "Image width in pixels. Defaults to " + ImageGenerationRequest.DEFAULT_WIDTH
                    + ". Some workflows use a fixed size or keep the reference photo's size and ignore it.";

    private static final String HEIGHT_DESCRIPTION =
            "Image height in pixels. Defaults to " + ImageGenerationRequest.DEFAULT_HEIGHT
                    + ". Some workflows use a fixed size or keep the reference photo's size and ignore it.";

    private static final String REFERENCE_IMAGES_DESCRIPTION =
            "Optional images that guide the result, at most " + MAX_REFERENCE_IMAGES
                    + ". Order matters. Omit to generate from the prompt alone.";

    private static final String DATA_DESCRIPTION =
            "Base64-encoded image bytes. A data URI prefix (data:image/png;base64,) is tolerated.";

    private static final String MIME_TYPE_DESCRIPTION =
            "The image format, which must match the bytes: image/png, image/jpeg or image/webp.";

    private static final String IMAGE_DESCRIPTION_DESCRIPTION = """
            A short description of what this image shows: its subjects, and whether a person is the \
            focus (for example "a man with glasses and a beard, head and shoulders"). Used only to choose \
            the workflow; it is never added to the prompt.""";

    private static final int CHOOSING_PERCENT = 5;
    private static final int CHOSEN_PERCENT = 15;

    /**
     * Shapes the {@code referenceImages} entry of the tool's input schema. Spring AI also builds a
     * typed copy of the argument from it, from which only the count and the descriptions are read:
     * the raw value is what reaches the service, which ignores {@code description}.
     */
    public record ReferenceImageInput(@McpToolParam(description = DATA_DESCRIPTION) String data,
                                      @McpToolParam(description = MIME_TYPE_DESCRIPTION) String mimeType,
                                      @McpToolParam(required = false, description = IMAGE_DESCRIPTION_DESCRIPTION) String description) {
    }

    private final CompiledGraph<ImageWorkflowState> imageWorkflowGraph;
    private final GraphRunner graphRunner;
    private final ComfyWorkflowRegistry comfyWorkflowRegistry;
    private final WorkflowImageGenerationService workflowImageGenerationService;

    public ImageGenerationTools(CompiledGraph<ImageWorkflowState> imageWorkflowGraph,
                                GraphRunner graphRunner,
                                ComfyWorkflowRegistry comfyWorkflowRegistry,
                                WorkflowImageGenerationService workflowImageGenerationService) {
        this.imageWorkflowGraph = imageWorkflowGraph;
        this.graphRunner = graphRunner;
        this.comfyWorkflowRegistry = comfyWorkflowRegistry;
        this.workflowImageGenerationService = workflowImageGenerationService;
    }

    @McpTool(name = GENERATE_IMAGE_AGENT, description = GENERATE_IMAGE_AGENT_DESCRIPTION)
    @PreAuthorize("hasAuthority('ROLE_MCP-GENERATE-IMAGE')")
    public CallToolResult generateImageAgent(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = PROMPT_DESCRIPTION) String prompt,
            @McpToolParam(required = false, description = WIDTH_DESCRIPTION) Integer width,
            @McpToolParam(required = false, description = HEIGHT_DESCRIPTION) Integer height,
            @McpToolParam(required = false, description = REFERENCE_IMAGES_DESCRIPTION) List<ReferenceImageInput> referenceImages
    ) {
        if (StringUtils.isBlank(prompt)) {
            return error("A non-empty prompt is required to generate an image.");
        }

        int referenceImageCount = referenceImages == null ? 0 : referenceImages.size();

        if (referenceImageCount > MAX_REFERENCE_IMAGES) {
            return error("At most %d reference images are accepted; %d supplied."
                    .formatted(MAX_REFERENCE_IMAGES, referenceImageCount));
        }

        Object rawReferenceImages = rawReferenceImages(mcpSyncRequestContext);

        //The typed copy and the raw value come from the same argument; if the raw one cannot be read,
        //generating without the images would silently ignore what the caller sent.
        if (referenceImageCount > 0 && rawReferenceImages == null) {
            return error("The attached reference images could not be read from the request.");
        }

        log.info("{} called: {} reference image(s), prompt of {} characters, requested size {}x{}",
                GENERATE_IMAGE_AGENT, referenceImageCount, prompt.length(), width, height);

        CallerIdentity callerIdentity = CallerIdentity.requireCurrent();
        ProgressReporter progressReporter = new ProgressReporter(mcpSyncRequestContext);
        progressReporter.emit(CHOOSING_PERCENT, "Choosing an image workflow…");

        List<String> referenceImageDescriptions = referenceImages == null ? List.of() : referenceImages.stream()
                .map(ReferenceImageInput::description)
                .map(ImageRequestMessage::trimDescription)
                .toList();

        ImageWorkflowSelection imageWorkflowSelection = selectWorkflow(
                prompt, referenceImageCount, referenceImageDescriptions, callerIdentity);

        Optional<RegisteredComfyWorkflow> registeredComfyWorkflow = comfyWorkflowRegistry.registeredWorkflows().stream()
                .filter(registeredWorkflow -> registeredWorkflow.toolName().equals(imageWorkflowSelection.toolName()))
                .findFirst();

        if (registeredComfyWorkflow.isEmpty()) {
            log.warn("The image router selected workflow '{}', which is not registered", imageWorkflowSelection.toolName());
            return error("The image router selected workflow '%s', which is not registered."
                    .formatted(imageWorkflowSelection.toolName()));
        }

        log.info("Running workflow '{}' ({}) with {} reference image(s); routing reason: {}",
                registeredComfyWorkflow.get().toolName(), registeredComfyWorkflow.get().name(), referenceImageCount,
                imageWorkflowSelection.rationale());

        progressReporter.emit(CHOSEN_PERCENT, "Using workflow " + registeredComfyWorkflow.get().name());

        ImageGenerationRequest imageGenerationRequest = new ImageGenerationRequest(
                imageWorkflowSelection.enhancedPrompt(),
                width == null ? ImageGenerationRequest.DEFAULT_WIDTH : width,
                height == null ? ImageGenerationRequest.DEFAULT_HEIGHT : height);

        CallToolResult generated = workflowImageGenerationService.generate(registeredComfyWorkflow.get(),
                imageGenerationRequest, rawReferenceImages, progressReporter);

        boolean failed = Boolean.TRUE.equals(generated.isError());

        log.info("{} finished with workflow '{}': {}", GENERATE_IMAGE_AGENT, imageWorkflowSelection.toolName(),
                failed ? "error result" : "image generated");

        return CallToolResult.builder(generated.content())
                .addTextContent("Workflow: %s. Why: %s".formatted(
                        imageWorkflowSelection.toolName(), imageWorkflowSelection.rationale()))
                .isError(failed)
                .build();
    }

    /**
     * The graph input carries the image count and descriptions, never the images.
     */
    private ImageWorkflowSelection selectWorkflow(String prompt, int referenceImageCount,
                                                  List<String> referenceImageDescriptions, CallerIdentity callerIdentity) {
        Map<String, Object> graphInput = Map.of(
                ImageWorkflowState.USER_PROMPT, prompt,
                ImageWorkflowState.REFERENCE_IMAGE_COUNT, referenceImageCount,
                ImageWorkflowState.REFERENCE_IMAGE_DESCRIPTIONS, referenceImageDescriptions,
                ImageWorkflowState.CALLER_IDENTITY, callerIdentity);

        NodeOutput<ImageWorkflowState> finalOutput = graphRunner.run(
                imageWorkflowGraph,
                GraphInput.args(graphInput),
                GraphThread.run(ImageWorkflowGraph.GRAPH_NAME, callerIdentity).runnableConfig(),
                output -> log.info("Image workflow graph reached node '{}'", output.node()));

        return finalOutput.state().workflowSelection()
                .orElseThrow(() -> new IllegalStateException("The image workflow graph completed without a selection"));
    }

    /**
     * The {@code referenceImages} value exactly as it arrived on the wire, or null when absent.
     */
    private static Object rawReferenceImages(McpSyncRequestContext mcpSyncRequestContext) {
        if (mcpSyncRequestContext.request() instanceof CallToolRequest callToolRequest
                && callToolRequest.arguments() != null) {
            return callToolRequest.arguments().get(PARAM_REFERENCE_IMAGES);
        }

        return null;
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .build();
    }
}
