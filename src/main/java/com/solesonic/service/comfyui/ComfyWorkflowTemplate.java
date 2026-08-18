package com.solesonic.service.comfyui;

import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_HEIGHT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_PROMPT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_SEED;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_WIDTH;

/**
 * One stored workflow, parsed and ready to patch. This is the entire blast radius of "ComfyUI is a
 * node graph" — nothing else in the codebase knows what a node is.
 *
 * <p>Values are bound by <em>token</em>: the stored document carries literal strings such as
 * {@code __PROMPT__} wherever a caller-supplied value belongs, and this class replaces them with
 * correctly typed JSON. Node ids and {@code _meta.title} values are therefore irrelevant, so a stock
 * API-format export works unmodified once its author drops tokens in. Everything not tokenised —
 * steps, cfg, sampler, scheduler, checkpoint — is left exactly as stored, which is what makes a row
 * a preset rather than a set of knobs.
 *
 * <p>Instances are immutable and safe to share: {@link #build} always patches a deep copy.
 */
public final class ComfyWorkflowTemplate {

    private static final Set<String> KNOWN_TOKENS = Set.of(TOKEN_PROMPT, TOKEN_SEED, TOKEN_WIDTH, TOKEN_HEIGHT);

    private final ObjectNode template;
    private final Set<String> tokens;

    private ComfyWorkflowTemplate(ObjectNode template, Set<String> tokens) {
        this.template = template;
        this.tokens = tokens;
    }

    /**
     * Parses one stored workflow, recording which tokens it actually carries.
     *
     * @throws ComfyUiException if the document is not a JSON object or carries no {@link
     *                          com.solesonic.mcp.config.comfyui.ComfyUiConstants#TOKEN_PROMPT}
     */
    public static ComfyWorkflowTemplate parse(String workflowJson, JsonMapper jsonMapper) {
        JsonNode parsed;

        try {
            parsed = jsonMapper.readTree(workflowJson);
        } catch (JacksonException jacksonException) {
            throw new ComfyUiException("ComfyUI workflow is not valid JSON", jacksonException);
        }

        if (!(parsed instanceof ObjectNode objectNode)) {
            throw new ComfyUiException("ComfyUI workflow is not a JSON object");
        }

        Set<String> tokens = Set.copyOf(collectTokens(objectNode, new HashSet<>()));

        if (!tokens.contains(TOKEN_PROMPT)) {
            throw new ComfyUiException("ComfyUI workflow has no " + TOKEN_PROMPT + " token, so it cannot accept a prompt");
        }

        return new ComfyWorkflowTemplate(objectNode, tokens);
    }

    /**
     * Whether the stored document carries the given token. Drives the tool's input schema: a
     * workflow without {@link com.solesonic.mcp.config.comfyui.ComfyUiConstants#TOKEN_WIDTH} does not
     * advertise a {@code width} parameter, so a client is never offered a knob that would be
     * silently ignored.
     */
    public boolean hasToken(String token) {
        return tokens.contains(token);
    }

    /**
     * Returns a patched copy of the workflow. The cached template is never mutated.
     */
    public ObjectNode build(ImageGenerationRequest imageGenerationRequest) {
        ObjectNode workflow = template.deepCopy();

        substitute(workflow, imageGenerationRequest);

        return workflow;
    }

    private static Set<String> collectTokens(JsonNode node, Set<String> found) {
        if (node instanceof ObjectNode objectNode) {
            for (Map.Entry<String, JsonNode> property : objectNode.properties()) {
                collectTokens(property.getValue(), found);
            }

            return found;
        }

        if (node instanceof ArrayNode arrayNode) {
            for (JsonNode element : arrayNode.values()) {
                collectTokens(element, found);
            }

            return found;
        }

        if (node.isString() && KNOWN_TOKENS.contains(node.stringValue())) {
            found.add(node.stringValue());
        }

        return found;
    }

    /**
     * Walks the whole document rather than a known set of node ids, so a token is honoured wherever
     * its author put it.
     */
    private void substitute(JsonNode node, ImageGenerationRequest imageGenerationRequest) {
        if (node instanceof ObjectNode objectNode) {
            for (Map.Entry<String, JsonNode> property : List.copyOf(objectNode.properties())) {
                JsonNode replacement = replacementFor(property.getValue(), imageGenerationRequest);

                if (replacement == null) {
                    substitute(property.getValue(), imageGenerationRequest);
                } else {
                    objectNode.set(property.getKey(), replacement);
                }
            }

            return;
        }

        if (node instanceof ArrayNode arrayNode) {
            for (int index = 0; index < arrayNode.size(); index++) {
                JsonNode element = arrayNode.get(index);
                JsonNode replacement = replacementFor(element, imageGenerationRequest);

                if (replacement == null) {
                    substitute(element, imageGenerationRequest);
                } else {
                    arrayNode.set(index, replacement);
                }
            }
        }
    }

    /**
     * Replacements are typed, not textual: a seed lands as a JSON number, never as the string
     * {@code "__SEED__"}, so ComfyUI receives exactly the shape it would have received from a
     * hand-edited workflow.
     */
    private JsonNode replacementFor(JsonNode node, ImageGenerationRequest imageGenerationRequest) {
        if (!node.isString()) {
            return null;
        }

        JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;

        return switch (node.stringValue()) {
            case TOKEN_PROMPT -> jsonNodeFactory.stringNode(imageGenerationRequest.prompt());
            case TOKEN_SEED -> jsonNodeFactory.numberNode(imageGenerationRequest.seed());
            case TOKEN_WIDTH -> jsonNodeFactory.numberNode(imageGenerationRequest.width());
            case TOKEN_HEIGHT -> jsonNodeFactory.numberNode(imageGenerationRequest.height());
            default -> null;
        };
    }
}
