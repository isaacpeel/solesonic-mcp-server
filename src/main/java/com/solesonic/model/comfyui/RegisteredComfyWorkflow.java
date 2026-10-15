package com.solesonic.model.comfyui;

import com.solesonic.agent.image.ImageIntent;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A stored workflow that survived validation and will be exposed as an MCP tool. Pairs the row's
 * identity — the columns a client sees when choosing a tool — with its parsed, ready-to-patch
 * template, its image-guided variants keyed by the number of reference images each takes, and the
 * intents it serves.
 */
public record RegisteredComfyWorkflow(
        String toolName,
        String name,
        String description,
        ComfyWorkflowTemplate template,
        Map<Integer, ComfyWorkflowTemplate> referenceVariants,
        Set<ImageIntent> intents
) {

    /**
     * Copies the variants into an unmodifiable map ordered by image count, so the accepted counts
     * always read in ascending order, and the intents into an unmodifiable set.
     */
    public RegisteredComfyWorkflow {
        referenceVariants = Collections.unmodifiableMap(new TreeMap<>(referenceVariants));
        intents = intents.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(intents));
    }

    public RegisteredComfyWorkflow(String toolName, String name, String description, ComfyWorkflowTemplate template,
                                   Map<Integer, ComfyWorkflowTemplate> referenceVariants) {
        this(toolName, name, description, template, referenceVariants, Set.of());
    }

    public RegisteredComfyWorkflow(String toolName, String name, String description, ComfyWorkflowTemplate template) {
        this(toolName, name, description, template, Map.of(), Set.of());
    }

    /**
     * The graph for exactly {@code imageCount} reference images, if this workflow has one.
     */
    public Optional<ComfyWorkflowTemplate> referenceVariant(int imageCount) {
        return Optional.ofNullable(referenceVariants.get(imageCount));
    }

    public boolean acceptsReferenceImages(int imageCount) {
        return referenceVariants.containsKey(imageCount);
    }

    /**
     * The numbers of reference images this workflow accepts, ascending; empty when it accepts none.
     */
    public List<Integer> acceptedReferenceImageCounts() {
        return List.copyOf(referenceVariants.keySet());
    }

    /**
     * Whether this workflow serves the given intent; an undecided ({@code null}) intent is served by none.
     */
    public boolean servesIntent(ImageIntent imageIntent) {
        return imageIntent != null && intents.contains(imageIntent);
    }
}
