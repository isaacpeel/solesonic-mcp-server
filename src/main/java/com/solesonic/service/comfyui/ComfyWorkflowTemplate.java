package com.solesonic.service.comfyui;

import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.CLASS_TYPE_CLIP_TEXT_ENCODE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.CLASS_TYPE_EMPTY_SD3_LATENT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.CLASS_TYPE_K_SAMPLER;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.FIELD_CLASS_TYPE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.FIELD_INPUTS;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.FIELD_META;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.FIELD_TITLE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.INPUT_HEIGHT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.INPUT_NOISE_SEED;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.INPUT_SEED;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.INPUT_STEPS;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.INPUT_TEXT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.INPUT_WIDTH;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.NODE_TITLE_LATENT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.NODE_TITLE_POSITIVE_PROMPT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.NODE_TITLE_SAMPLER;

/**
 * Owns the ComfyUI API-format workflow graph. This is the entire blast radius of "ComfyUI is a node
 * graph" — nothing else in the codebase knows what a node is.
 *
 * <p>Nodes are bound by {@code _meta.title} rather than by node id: ids are assigned by the ComfyUI
 * editor and shift on every re-export, and this graph contains two {@code CLIPTextEncode} nodes, so
 * an id shift that landed the prompt on the negative node would produce a perfectly successful
 * generation of the wrong image with no error anywhere.
 */
@Component
public class ComfyWorkflowTemplate {

    private static final Logger log = LoggerFactory.getLogger(ComfyWorkflowTemplate.class);

    private final ObjectNode template;
    private final String positivePromptNodeId;
    private final String samplerNodeId;
    private final String latentNodeId;
    private final String seedInputKey;

    public ComfyWorkflowTemplate(
            @Value("${comfyui.workflow.flux-schnell}") Resource workflowResource,
            JsonMapper jsonMapper
    ) {
        this.template = readTemplate(workflowResource, jsonMapper);

        this.positivePromptNodeId = resolveNodeId(NODE_TITLE_POSITIVE_PROMPT, CLASS_TYPE_CLIP_TEXT_ENCODE);
        this.samplerNodeId = resolveNodeId(NODE_TITLE_SAMPLER, CLASS_TYPE_K_SAMPLER);
        this.latentNodeId = resolveNodeId(NODE_TITLE_LATENT, CLASS_TYPE_EMPTY_SD3_LATENT);
        this.seedInputKey = resolveSeedInputKey(this.samplerNodeId);

        log.info("ComfyUI workflow loaded from {}. Prompt node: {}, sampler node: {} (seed key '{}'), latent node: {}",
                workflowResource.getDescription(), positivePromptNodeId, samplerNodeId, seedInputKey, latentNodeId);
    }

    /**
     * Returns a patched copy of the workflow. The cached template is never mutated.
     */
    public ObjectNode build(ImageGenerationRequest request) {
        ObjectNode workflow = template.deepCopy();

        inputsOf(workflow, positivePromptNodeId).put(INPUT_TEXT, request.prompt());

        ObjectNode samplerInputs = inputsOf(workflow, samplerNodeId);
        samplerInputs.put(seedInputKey, request.seed());
        samplerInputs.put(INPUT_STEPS, request.steps());

        ObjectNode latentInputs = inputsOf(workflow, latentNodeId);
        latentInputs.put(INPUT_WIDTH, request.width());
        latentInputs.put(INPUT_HEIGHT, request.height());

        return workflow;
    }

    private ObjectNode readTemplate(Resource workflowResource, JsonMapper jsonMapper) {
        JsonNode parsed;

        try (InputStream inputStream = workflowResource.getInputStream()) {
            parsed = jsonMapper.readTree(inputStream);
        } catch (IOException ioException) {
            throw new ComfyUiException("Unable to read ComfyUI workflow resource: " + workflowResource.getDescription(), ioException);
        }

        if (!(parsed instanceof ObjectNode objectNode)) {
            throw new ComfyUiException("ComfyUI workflow resource is not a JSON object: " + workflowResource.getDescription());
        }

        return objectNode;
    }

    private String resolveNodeId(String title, String expectedClassType) {
        for (Map.Entry<String, JsonNode> node : template.properties()) {
            JsonNode candidate = node.getValue();
            String candidateTitle = candidate.path(FIELD_META).path(FIELD_TITLE).asString(null);

            if (!title.equals(candidateTitle)) {
                continue;
            }

            String candidateClassType = candidate.path(FIELD_CLASS_TYPE).asString(null);

            if (!expectedClassType.equals(candidateClassType)) {
                throw new ComfyUiException("ComfyUI workflow node titled '" + title + "' has class_type '"
                        + candidateClassType + "', expected '" + expectedClassType + "'");
            }

            if (!candidate.path(FIELD_INPUTS).isObject()) {
                throw new ComfyUiException("ComfyUI workflow node titled '" + title + "' has no inputs object");
            }

            return node.getKey();
        }

        throw new ComfyUiException("ComfyUI workflow has no node titled '" + title + "'");
    }

    /**
     * This workflow's {@code KSampler} uses {@code inputs.seed}; other Flux templates use
     * {@code RandomNoise} with {@code inputs.noise_seed}. Accept whichever key is already present.
     */
    private String resolveSeedInputKey(String nodeId) {
        JsonNode inputs = template.path(nodeId).path(FIELD_INPUTS);

        if (inputs.has(INPUT_SEED)) {
            return INPUT_SEED;
        }

        if (inputs.has(INPUT_NOISE_SEED)) {
            return INPUT_NOISE_SEED;
        }

        throw new ComfyUiException("ComfyUI workflow sampler node '" + nodeId + "' has neither '"
                + INPUT_SEED + "' nor '" + INPUT_NOISE_SEED + "' input");
    }

    private ObjectNode inputsOf(ObjectNode workflow, String nodeId) {
        return (ObjectNode) workflow.get(nodeId).get(FIELD_INPUTS);
    }
}
