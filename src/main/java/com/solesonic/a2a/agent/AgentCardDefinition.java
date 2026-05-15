package com.solesonic.a2a.agent;

import java.util.List;

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
