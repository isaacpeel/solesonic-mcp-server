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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MAX_REFERENCE_IMAGES;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_HEIGHT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_PROMPT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_REFERENCE_IMAGE_PREFIX;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_SEED;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_SUFFIX;
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
 * <p>Reference image slots ({@code __REFERENCE_IMAGE_1__} …) are counted at parse time and must be
 * contiguous from 1: a graph is fixed, so the number of images it loads is a property of the
 * document, and a gap would leave a {@code LoadImage} node holding a name ComfyUI cannot resolve.
 *
 * <p>Instances are immutable and safe to share: {@link #build} always patches a deep copy.
 */
public final class ComfyWorkflowTemplate {

    private static final Set<String> KNOWN_TOKENS = Set.of(TOKEN_PROMPT, TOKEN_SEED, TOKEN_WIDTH, TOKEN_HEIGHT);

    private static final Pattern REFERENCE_TOKEN = Pattern.compile(
            Pattern.quote(TOKEN_REFERENCE_IMAGE_PREFIX) + "(\\d{1,3})" + Pattern.quote(TOKEN_SUFFIX));

    private final ObjectNode template;
    private final Set<String> tokens;
    private final int referenceImageSlots;

    private ComfyWorkflowTemplate(ObjectNode template, Set<String> tokens, int referenceImageSlots) {
        this.template = template;
        this.tokens = tokens;
        this.referenceImageSlots = referenceImageSlots;
    }

    /**
     * Parses one stored workflow, recording which tokens it actually carries.
     *
     * @throws ComfyUiException if the document is not a JSON object, carries no {@link
     *                          com.solesonic.mcp.config.comfyui.ComfyUiConstants#TOKEN_PROMPT}, or
     *                          declares reference slots that are not contiguous from 1 or exceed
     *                          {@link com.solesonic.mcp.config.comfyui.ComfyUiConstants#MAX_REFERENCE_IMAGES}
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

        int referenceImageSlots = validatedSlotCount(collectReferenceSlots(objectNode, new TreeSet<>()));

        return new ComfyWorkflowTemplate(objectNode, tokens, referenceImageSlots);
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
     * How many reference images this graph loads; zero for a text-only graph.
     */
    public int referenceImageSlots() {
        return referenceImageSlots;
    }

    /**
     * Returns a patched copy of a graph that loads no reference images. The cached template is
     * never mutated.
     */
    public ObjectNode build(ImageGenerationRequest imageGenerationRequest) {
        return build(imageGenerationRequest, List.of());
    }

    /**
     * Returns a patched copy of the workflow. The cached template is never mutated.
     *
     * @param referenceImageValues the {@code LoadImage} value for each slot in order, as ComfyUI
     *                             returned it from the upload; must match the slot count exactly
     */
    public ObjectNode build(ImageGenerationRequest imageGenerationRequest, List<String> referenceImageValues) {
        if (referenceImageValues.size() != referenceImageSlots) {
            throw new ComfyUiException("ComfyUI workflow takes %d reference image(s); %d supplied"
                    .formatted(referenceImageSlots, referenceImageValues.size()));
        }

        ObjectNode workflow = template.deepCopy();

        substitute(workflow, imageGenerationRequest, referenceImageValues);

        return workflow;
    }

    private static int validatedSlotCount(Set<Integer> slots) {
        if (slots.isEmpty()) {
            return 0;
        }

        if (slots.size() > MAX_REFERENCE_IMAGES) {
            throw new ComfyUiException("ComfyUI workflow declares %d reference image slots; at most %d are supported"
                    .formatted(slots.size(), MAX_REFERENCE_IMAGES));
        }

        int expected = 1;

        for (int slot : slots) {
            if (slot != expected) {
                throw new ComfyUiException("ComfyUI workflow reference image slots must be contiguous from 1, found "
                        + slots);
            }

            expected++;
        }

        return slots.size();
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

    private static Set<Integer> collectReferenceSlots(JsonNode node, Set<Integer> found) {
        if (node instanceof ObjectNode objectNode) {
            for (Map.Entry<String, JsonNode> property : objectNode.properties()) {
                collectReferenceSlots(property.getValue(), found);
            }

            return found;
        }

        if (node instanceof ArrayNode arrayNode) {
            for (JsonNode element : arrayNode.values()) {
                collectReferenceSlots(element, found);
            }

            return found;
        }

        Integer slot = referenceSlot(node);

        if (slot != null) {
            found.add(slot);
        }

        return found;
    }

    private static Integer referenceSlot(JsonNode node) {
        if (!node.isString()) {
            return null;
        }

        Matcher matcher = REFERENCE_TOKEN.matcher(node.stringValue());

        return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /**
     * Walks the whole document rather than a known set of node ids, so a token is honoured wherever
     * its author put it.
     */
    private void substitute(JsonNode node, ImageGenerationRequest imageGenerationRequest, List<String> referenceImageValues) {
        if (node instanceof ObjectNode objectNode) {
            for (Map.Entry<String, JsonNode> property : List.copyOf(objectNode.properties())) {
                JsonNode replacement = replacementFor(property.getValue(), imageGenerationRequest, referenceImageValues);

                if (replacement == null) {
                    substitute(property.getValue(), imageGenerationRequest, referenceImageValues);
                } else {
                    objectNode.set(property.getKey(), replacement);
                }
            }

            return;
        }

        if (node instanceof ArrayNode arrayNode) {
            for (int index = 0; index < arrayNode.size(); index++) {
                JsonNode element = arrayNode.get(index);
                JsonNode replacement = replacementFor(element, imageGenerationRequest, referenceImageValues);

                if (replacement == null) {
                    substitute(element, imageGenerationRequest, referenceImageValues);
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
    private JsonNode replacementFor(JsonNode node, ImageGenerationRequest imageGenerationRequest, List<String> referenceImageValues) {
        if (!node.isString()) {
            return null;
        }

        JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;

        Integer slot = referenceSlot(node);

        if (slot != null) {
            return jsonNodeFactory.stringNode(referenceImageValues.get(slot - 1));
        }

        return switch (node.stringValue()) {
            case TOKEN_PROMPT -> jsonNodeFactory.stringNode(imageGenerationRequest.prompt());
            case TOKEN_SEED -> jsonNodeFactory.numberNode(imageGenerationRequest.seed());
            case TOKEN_WIDTH -> jsonNodeFactory.numberNode(imageGenerationRequest.width());
            case TOKEN_HEIGHT -> jsonNodeFactory.numberNode(imageGenerationRequest.height());
            default -> null;
        };
    }
}
