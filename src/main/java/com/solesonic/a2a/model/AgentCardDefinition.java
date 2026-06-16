package com.solesonic.a2a.model;

import java.util.List;

/**
 * Represents an agent card definition loaded from a JSON resource file under
 * {@code src/main/resources/agents/} (e.g. {@code nba-agent.json}). Each file
 * declares a single agent's identity, capabilities, and skills, which are
 * deserialized into this record at startup.
 *
 * @param id unique identifier for the agent
 * @param name human-readable display name
 * @param description summary of what the agent does
 * @param version version string for the agent definition
 * @param urlPath URL path under which the agent is exposed
 * @param protocolVersion A2A protocol version the agent conforms to
 * @param capabilities streaming and push-notification flags for the agent
 * @param defaultInputModes MIME types the agent accepts as input by default
 * @param defaultOutputModes MIME types the agent produces as output by default
 * @param skills list of discrete skills the agent supports
 */
public record AgentCardDefinition(
        String id,
        String name,
        String description,
        String version,
        String urlPath,
        String protocolVersion,
        Capabilities capabilities,
        List<String> defaultInputModes,
        List<String> defaultOutputModes,
        List<Skill> skills
) {
    public record Capabilities(boolean streaming, boolean pushNotifications) {}

    public record Skill(String id, String name, String description, List<String> tags) {}
}
