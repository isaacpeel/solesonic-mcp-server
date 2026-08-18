package com.solesonic.service.comfyui;

import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ComfyWorkflow;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.repository.comfyui.ComfyWorkflowRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Loads the enabled rows of {@code comfy_workflow} once at startup and parses each into a
 * {@link ComfyWorkflowTemplate}, so the cost of parsing is paid once rather than per tool call.
 *
 * <p>Rows are validated independently and a bad row is skipped with a warning rather than failing
 * the context. Rows are typed in by hand, so one malformed paste must not be able to stop the server
 * booting and take every unrelated tool down with it.
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
            log.warn("No usable ComfyUI workflows found in comfy_workflow. No image generation tools will be registered.");
        } else {
            log.info("Loaded {} ComfyUI workflow(s): {}", registeredWorkflows.size(),
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

            log.info("Registering ComfyUI workflow tool '{}' ({})", row.getToolName(), row.getName());

            return new RegisteredComfyWorkflow(row.getToolName(), row.getName(), row.getDescription(), template);
        } catch (ComfyUiException comfyUiException) {
            log.warn("Skipping ComfyUI workflow '{}': {}", row.getToolName(), comfyUiException.getMessage());

            return null;
        }
    }
}
