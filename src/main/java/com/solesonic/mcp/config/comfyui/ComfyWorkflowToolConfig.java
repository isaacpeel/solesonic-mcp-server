package com.solesonic.mcp.config.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.mcp.tool.comfyui.WorkflowImageGenerationService;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.service.comfyui.ComfyWorkflowRegistry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.DefaultMcpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.META_KIND;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.META_KIND_IMAGE_GENERATION;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MIME_TYPE_JPEG;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MIME_TYPE_PNG;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MIME_TYPE_WEBP;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_HEIGHT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_WIDTH;
import static com.solesonic.mcp.tool.comfyui.ReferenceImageArguments.FIELD_DATA;
import static com.solesonic.mcp.tool.comfyui.ReferenceImageArguments.FIELD_MIME_TYPE;
import static com.solesonic.mcp.tool.comfyui.ReferenceImageArguments.PARAM_REFERENCE_IMAGES;

/**
 * Turns every stored workflow into an MCP tool at startup, so choosing a tool is how a client
 * chooses a workflow.
 *
 * <p>Spring AI's {@code McpServerAutoConfiguration} collects tools as
 * {@code ObjectProvider<List<SyncToolSpecification>>} and flat-maps every matching bean, so this
 * list is merged additively with the annotation-scanned {@code @McpTool} methods rather than
 * replacing them.
 */
@Configuration
public class ComfyWorkflowToolConfig {

    private static final Logger log = LoggerFactory.getLogger(ComfyWorkflowToolConfig.class);

    private static final String PARAM_PROMPT = "prompt";
    private static final String PARAM_WIDTH = "width";
    private static final String PARAM_HEIGHT = "height";

    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_DESCRIPTION = "description";
    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_REQUIRED = "required";
    private static final String SCHEMA_ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_ARRAY = "array";
    private static final String SCHEMA_ITEMS = "items";
    private static final String SCHEMA_MIN_ITEMS = "minItems";
    private static final String SCHEMA_MAX_ITEMS = "maxItems";
    private static final String SCHEMA_ENUM = "enum";

    private static final String PROMPT_DESCRIPTION = """
            Describe the image to generate. Include subject, style, lighting, and composition.
            Longer, more specific prompts produce better results.
            """;

    private static final String WIDTH_DESCRIPTION =
            "Image width in pixels. Defaults to " + ImageGenerationRequest.DEFAULT_WIDTH + " when omitted.";

    private static final String HEIGHT_DESCRIPTION =
            "Image height in pixels. Defaults to " + ImageGenerationRequest.DEFAULT_HEIGHT + " when omitted.";

    private static final String DATA_DESCRIPTION =
            "Base64-encoded image bytes. A data URI prefix (data:image/png;base64,) is tolerated.";

    private static final String MIME_TYPE_DESCRIPTION = "The image format; must match the bytes.";

    @Bean
    public List<SyncToolSpecification> comfyWorkflowToolSpecifications(
            ComfyWorkflowRegistry comfyWorkflowRegistry,
            WorkflowImageGenerationService workflowImageGenerationService
    ) {
        List<SyncToolSpecification> toolSpecifications = comfyWorkflowRegistry.registeredWorkflows().stream()
                .map(registeredComfyWorkflow -> toolSpecification(registeredComfyWorkflow, workflowImageGenerationService))
                .toList();

        log.info("Registered {} ComfyUI workflow tool(s)", toolSpecifications.size());

        return toolSpecifications;
    }

    private SyncToolSpecification toolSpecification(
            RegisteredComfyWorkflow registeredComfyWorkflow,
            WorkflowImageGenerationService workflowImageGenerationService
    ) {
        Tool tool = Tool.builder(registeredComfyWorkflow.toolName(), inputSchema(registeredComfyWorkflow))
                .title(registeredComfyWorkflow.name())
                .description(registeredComfyWorkflow.description())
                .meta(Map.of(META_KIND, META_KIND_IMAGE_GENERATION))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, callToolRequest) ->
                        handle(registeredComfyWorkflow, workflowImageGenerationService, exchange, callToolRequest))
                .build();
    }

    /**
     * The schema is built from the tokens the stored workflow actually carries, so a workflow
     * without {@code __WIDTH__} never advertises a {@code width} parameter — a client is not offered
     * a knob that would be accepted and then silently ignored.
     */
    private Map<String, Object> inputSchema(RegisteredComfyWorkflow registeredComfyWorkflow) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(PARAM_PROMPT, Map.of(SCHEMA_TYPE, TYPE_STRING, SCHEMA_DESCRIPTION, PROMPT_DESCRIPTION));

        if (registeredComfyWorkflow.template().hasToken(TOKEN_WIDTH)) {
            properties.put(PARAM_WIDTH, Map.of(SCHEMA_TYPE, TYPE_INTEGER, SCHEMA_DESCRIPTION, WIDTH_DESCRIPTION));
        }

        if (registeredComfyWorkflow.template().hasToken(TOKEN_HEIGHT)) {
            properties.put(PARAM_HEIGHT, Map.of(SCHEMA_TYPE, TYPE_INTEGER, SCHEMA_DESCRIPTION, HEIGHT_DESCRIPTION));
        }

        if (registeredComfyWorkflow.referenceImageSlots() > 0) {
            properties.put(PARAM_REFERENCE_IMAGES, referenceImagesSchema(registeredComfyWorkflow.referenceImageSlots()));
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(SCHEMA_TYPE, TYPE_OBJECT);
        schema.put(SCHEMA_PROPERTIES, properties);
        schema.put(SCHEMA_REQUIRED, List.of(PARAM_PROMPT));
        schema.put(SCHEMA_ADDITIONAL_PROPERTIES, false);

        return schema;
    }

    /**
     * {@code minItems} and {@code maxItems} both equal the slot count: the graph is fixed, so the
     * workflow takes exactly that many images or none, and a client can read the number from the
     * schema rather than from prose. Advertised only for rows with a reference variant.
     */
    private Map<String, Object> referenceImagesSchema(int slots) {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put(FIELD_DATA, Map.of(SCHEMA_TYPE, TYPE_STRING, SCHEMA_DESCRIPTION, DATA_DESCRIPTION));
        itemProperties.put(FIELD_MIME_TYPE, Map.of(
                SCHEMA_TYPE, TYPE_STRING,
                SCHEMA_DESCRIPTION, MIME_TYPE_DESCRIPTION,
                SCHEMA_ENUM, List.of(MIME_TYPE_PNG, MIME_TYPE_JPEG, MIME_TYPE_WEBP)));

        Map<String, Object> items = new LinkedHashMap<>();
        items.put(SCHEMA_TYPE, TYPE_OBJECT);
        items.put(SCHEMA_PROPERTIES, itemProperties);
        items.put(SCHEMA_REQUIRED, List.of(FIELD_DATA, FIELD_MIME_TYPE));
        items.put(SCHEMA_ADDITIONAL_PROPERTIES, false);

        Map<String, Object> referenceImages = new LinkedHashMap<>();
        referenceImages.put(SCHEMA_TYPE, TYPE_ARRAY);
        referenceImages.put(SCHEMA_DESCRIPTION, """
                Optional images that guide the result. When supplied, exactly %d must be given; order \
                matters (image 1 fills slot 1). Omit to generate from the prompt alone.""".formatted(slots));
        referenceImages.put(SCHEMA_MIN_ITEMS, slots);
        referenceImages.put(SCHEMA_MAX_ITEMS, slots);
        referenceImages.put(SCHEMA_ITEMS, items);

        return referenceImages;
    }

    /**
     * Rebuilds the {@link McpSyncRequestContext} that the annotation-driven path would have injected,
     * so progress notifications keep working exactly as they did for the old {@code generate_image}
     * tool. The request carries the progress token, so both halves are required.
     *
     * <p>The raw {@code reference_images} value is handed on undecoded: decoding happens inside
     * {@link WorkflowImageGenerationService}, behind its role check. The raw arguments map is never
     * logged: it carries the image data.
     */
    private CallToolResult handle(
            RegisteredComfyWorkflow registeredComfyWorkflow,
            WorkflowImageGenerationService workflowImageGenerationService,
            McpSyncServerExchange exchange,
            CallToolRequest callToolRequest
    ) {
        McpSyncRequestContext mcpSyncRequestContext = DefaultMcpSyncRequestContext.builder()
                .request(callToolRequest)
                .exchange(exchange)
                .build();

        Map<String, Object> arguments = callToolRequest.arguments() == null ? Map.of() : callToolRequest.arguments();

        ImageGenerationRequest imageGenerationRequest = new ImageGenerationRequest(
                stringArgument(arguments),
                intArgument(arguments, PARAM_WIDTH, ImageGenerationRequest.DEFAULT_WIDTH),
                intArgument(arguments, PARAM_HEIGHT, ImageGenerationRequest.DEFAULT_HEIGHT));

        return workflowImageGenerationService.generate(registeredComfyWorkflow, imageGenerationRequest,
                arguments.get(PARAM_REFERENCE_IMAGES), new ProgressReporter(mcpSyncRequestContext));
    }

    private String stringArgument(Map<String, Object> arguments) {
        Object value = arguments.get(PARAM_PROMPT);

        return value == null ? null : value.toString();
    }

    /**
     * JSON numbers arrive as whatever the mapper chose — {@code Integer}, {@code Long} or
     * {@code Double} — so the value is narrowed through {@link Number} rather than cast.
     */
    private int intArgument(Map<String, Object> arguments, String name, int defaultValue) {
        Object value = arguments.get(name);

        if (value instanceof Number number) {
            return number.intValue();
        }

        return defaultValue;
    }
}
