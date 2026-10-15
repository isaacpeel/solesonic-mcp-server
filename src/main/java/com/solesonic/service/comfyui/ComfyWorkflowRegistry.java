package com.solesonic.service.comfyui;

import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ComfyWorkflow;
import com.solesonic.model.comfyui.ComfyWorkflowReferenceVariant;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.repository.comfyui.ComfyWorkflowRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loads the enabled rows of {@code comfy_workflow} once at startup and parses each into a
 * {@link ComfyWorkflowTemplate}, so the cost of parsing is paid once rather than per tool call.
 *
 * <p>Rows are validated independently and a bad row is skipped with a warning rather than failing
 * the context. Rows are typed in by hand, so one malformed paste must not be able to stop the server
 * booting and take every unrelated tool down with it. The same holds one level down: each of a row's
 * reference variants is validated on its own, and a bad one is dropped while the row's text-only
 * tool and its other variants still register.
 */
@Component
public class ComfyWorkflowRegistry {

    private static final Logger log = LoggerFactory.getLogger(ComfyWorkflowRegistry.class);

    /**
     * MCP tool names are addressed by the client as identifiers; anything outside this set would be
     * registered but effectively uncallable.
     */
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,128}$");

    private final ComfyWorkflowRepository comfyWorkflowRepository;
    private final JsonMapper jsonMapper;

    private List<RegisteredComfyWorkflow> registeredWorkflows = List.of();

    public ComfyWorkflowRegistry(ComfyWorkflowRepository comfyWorkflowRepository, JsonMapper jsonMapper) {
        this.comfyWorkflowRepository = comfyWorkflowRepository;
        this.jsonMapper = jsonMapper;
    }

    @PostConstruct
    public void loadWorkflows() {
        List<ComfyWorkflow> rows = comfyWorkflowRepository.findAllByEnabledTrueOrderByToolNameAsc();
        List<RegisteredComfyWorkflow> loaded = new ArrayList<>();

        for (ComfyWorkflow row : rows) {
            RegisteredComfyWorkflow registered = validate(row);

            if (registered != null) {
                loaded.add(registered);
            }
        }

        registeredWorkflows = List.copyOf(loaded);

        if (registeredWorkflows.isEmpty()) {
            log.warn("No usable ComfyUI workflows found in comfy_workflow. The image router has nothing to choose from.");
        } else {
            log.info("Loaded {} ComfyUI workflow(s) available to the image router: {}", registeredWorkflows.size(),
                    registeredWorkflows.stream().map(RegisteredComfyWorkflow::toolName).toList());
        }
    }

    public List<RegisteredComfyWorkflow> registeredWorkflows() {
        return registeredWorkflows;
    }

    /**
     * @return the validated workflow, or {@code null} if the row is unusable and was skipped
     */
    private RegisteredComfyWorkflow validate(ComfyWorkflow row) {
        if (!TOOL_NAME_PATTERN.matcher(row.getToolName() == null ? "" : row.getToolName()).matches()) {
            log.warn("Skipping ComfyUI workflow {}: tool_name '{}' is not a legal MCP tool name",
                    row.getId(), row.getToolName());

            return null;
        }

        try {
            ComfyWorkflowTemplate template = ComfyWorkflowTemplate.parse(row.getWorkflowJson(), jsonMapper);

            //A LoadImage node with no image fails ComfyUI validation, so a text-only graph that loads
            //images could never succeed on the path that supplies none.
            if (template.referenceImageSlots() > 0) {
                log.warn("Skipping ComfyUI workflow '{}': workflow_json carries reference image tokens; "
                        + "image-guided graphs belong in comfy_workflow_reference_variant", row.getToolName());

                return null;
            }

            Map<Integer, ComfyWorkflowTemplate> referenceVariants = referenceVariants(row);

            log.info("Registering ComfyUI workflow tool '{}' ({})", row.getToolName(), row.getName());

            if (!referenceVariants.isEmpty()) {
                log.info("ComfyUI workflow tool '{}' accepts {} reference image(s)", row.getToolName(),
                        referenceVariants.keySet().stream().map(String::valueOf).collect(Collectors.joining(", ")));
            }

            if (!row.getIntents().isEmpty()) {
                log.info("ComfyUI workflow tool '{}' serves intents {}", row.getToolName(), row.getIntents());
            }

            return new RegisteredComfyWorkflow(row.getToolName(), row.getName(), row.getDescription(), template,
                    referenceVariants, row.getIntents());
        } catch (ComfyUiException comfyUiException) {
            log.warn("Skipping ComfyUI workflow '{}': {}", row.getToolName(), comfyUiException.getMessage());

            return null;
        }
    }

    /**
     * Parses each image-guided variant on its own, so one bad variant drops only itself.
     *
     * @return the usable variants keyed by image count; empty if the row has none
     */
    private Map<Integer, ComfyWorkflowTemplate> referenceVariants(ComfyWorkflow row) {
        Map<Integer, ComfyWorkflowTemplate> referenceVariants = new TreeMap<>();

        for (ComfyWorkflowReferenceVariant referenceVariant : row.getReferenceVariants()) {
            ComfyWorkflowTemplate referenceTemplate = referenceVariant(row, referenceVariant);

            if (referenceTemplate == null) {
                continue;
            }

            if (referenceVariants.putIfAbsent(referenceVariant.imageCount(), referenceTemplate) != null) {
                log.warn("Ignoring a duplicate {}-image reference variant of ComfyUI workflow '{}'",
                        referenceVariant.imageCount(), row.getToolName());
            }
        }

        return referenceVariants;
    }

    /**
     * @return the parsed variant, or {@code null} if it is unusable
     */
    private ComfyWorkflowTemplate referenceVariant(ComfyWorkflow row, ComfyWorkflowReferenceVariant referenceVariant) {
        int imageCount = referenceVariant.imageCount();

        try {
            ComfyWorkflowTemplate referenceTemplate = ComfyWorkflowTemplate.parse(referenceVariant.workflowJson(), jsonMapper);

            if (referenceTemplate.referenceImageSlots() == 0) {
                log.warn("Ignoring the {}-image reference variant of ComfyUI workflow '{}': it has no __REFERENCE_IMAGE_1__ token",
                        imageCount, row.getToolName());

                return null;
            }

            //The image count picks the graph at call time, so a graph loading a different number of
            //images would be handed the wrong number of uploads.
            if (referenceTemplate.referenceImageSlots() != imageCount) {
                log.warn("Ignoring the {}-image reference variant of ComfyUI workflow '{}': its graph loads {} image(s)",
                        imageCount, row.getToolName(), referenceTemplate.referenceImageSlots());

                return null;
            }

            return referenceTemplate;
        } catch (ComfyUiException comfyUiException) {
            log.warn("Ignoring the {}-image reference variant of ComfyUI workflow '{}': {}",
                    imageCount, row.getToolName(), comfyUiException.getMessage());

            return null;
        }
    }
}
