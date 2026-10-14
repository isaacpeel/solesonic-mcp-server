package com.solesonic.agent.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * How the image router maps a request onto stored workflows.
 *
 * @param defaultWorkflow the {@code tool_name} used when the router's pick is missing or unknown
 * @param intentWorkflows the {@code tool_name}s that serve each intent. An intent with no entry, or
 *                        whose workflows cannot take the attached images, is served by every
 *                        workflow that can.
 */
@ConfigurationProperties(prefix = "solesonic.image")
public record ImageRoutingProperties(String defaultWorkflow, Map<ImageIntent, List<String>> intentWorkflows) {

    public ImageRoutingProperties {
        intentWorkflows = intentWorkflows == null ? Map.of() : Map.copyOf(intentWorkflows);
    }

    public List<String> workflowsFor(ImageIntent imageIntent) {
        if (imageIntent == null) {
            return List.of();
        }

        return intentWorkflows.getOrDefault(imageIntent, List.of());
    }
}
