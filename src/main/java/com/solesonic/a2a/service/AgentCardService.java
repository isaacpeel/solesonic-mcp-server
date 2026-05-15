package com.solesonic.a2a.service;

import com.solesonic.a2a.agent.AgentCardDefinition;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

@Service
public class AgentCardService {

    @Value("${solesonic.a2a.base.uri}")
    private String a2aBaseUri;

    @Value("classpath:agents/*.json")
    private Resource[] agentConfigs;

    private final JsonMapper jsonMapper;

    private Map<String, AgentCard> agentCardsById;

    public AgentCardService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @PostConstruct
    public void loadAgentCards() {
        try {
            Map<String, AgentCard> agentCards = new LinkedHashMap<>();

            for (Resource resource : agentConfigs) {
                AgentCardDefinition definition = jsonMapper.readValue(resource.getInputStream(), AgentCardDefinition.class);
                AgentCard card = buildAgentCard(definition, a2aBaseUri);
                agentCards.put(definition.id(), card);
            }

            this.agentCardsById = Collections.unmodifiableMap(agentCards);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load agent cards from classpath:agents/", exception);
        }
    }

    public List<AgentCard> allAgentCards() {
        return List.copyOf(agentCardsById.values());
    }

    public List<String> allAgentCardIds() {
        return List.copyOf(agentCardsById.keySet());
    }

    public Optional<AgentCard> agentCardById(String id) {
        return Optional.ofNullable(agentCardsById.get(id));
    }

    public AgentCard agentCardByName(String name) {
        return allAgentCards().stream()
                .filter(agentCard -> agentCard.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent Not Found"));
    }

    private AgentCard buildAgentCard(AgentCardDefinition definition, String baseUrl) {
        AgentCapabilities capabilities = new AgentCapabilities.Builder()
                .streaming(definition.capabilities().streaming())
                .pushNotifications(definition.capabilities().pushNotifications())
                .build();

        List<AgentSkill> skills = definition.skills().stream()
                .map(skill -> new AgentSkill.Builder()
                        .id(skill.id())
                        .name(skill.name())
                        .description(skill.description())
                        .tags(skill.tags())
                        .build())
                .toList();

        String cardUri = baseUrl + definition.urlPath();

        return new AgentCard.Builder()
                .name(definition.name())
                .description(definition.description())
                .url(cardUri)
                .version(definition.version())
                .capabilities(capabilities)
                .defaultInputModes(definition.defaultInputModes())
                .defaultOutputModes(definition.defaultOutputModes())
                .skills(skills)
                .protocolVersion(definition.protocolVersion())
                .build();
    }
}
