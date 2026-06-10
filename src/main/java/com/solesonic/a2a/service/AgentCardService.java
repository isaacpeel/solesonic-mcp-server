package com.solesonic.a2a.service;

import com.solesonic.a2a.model.AgentCardDefinition;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class AgentCardService {
    private static final Logger log = LoggerFactory.getLogger(AgentCardService.class);

    public static final String A2A = "a2a";
    public static final String WELL_KNOWN = ".well-known";
    public static final String AGENT_JSON = "agent.json";
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
        Map<String, AgentCard> agentCards = new LinkedHashMap<>();

        for (Resource resource : agentConfigs) {
            try(InputStream inputStream = resource.getInputStream()) {
                AgentCardDefinition cardDefinition = jsonMapper.readValue(inputStream, AgentCardDefinition.class);
                AgentCard agentCard = buildAgentCard(cardDefinition);
                agentCards.put(cardDefinition.id(), agentCard);
            } catch (IOException runtimeException) {
                throw new RuntimeException(runtimeException);
            }
        }

        this.agentCardsById = Collections.unmodifiableMap(agentCards);
    }

    public List<AgentCard> allAgentCards() {
        return List.copyOf(agentCardsById.values());
    }

    public List<String> allAgentCardUris() {
        return allAgentCards().stream()
                .map(agentCard -> UriComponentsBuilder.fromUriString(agentCard.url())
                        .pathSegment(WELL_KNOWN)
                        .pathSegment(AGENT_JSON)
                        .build()
                        .toUriString())
                .toList();
    }

    @SuppressWarnings("unused")
    public List<String> allAgentCardIds() {
        return List.copyOf(agentCardsById.keySet());
    }

    public Optional<AgentCard> agentCardById(String agentId) {
        return Optional.ofNullable(agentCardsById.get(agentId));
    }

    @SuppressWarnings("unused")
    public AgentCard agentCardByName(String name) {
        return allAgentCards().stream()
                .filter(agentCard -> agentCard.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent Not Found"));
    }

    private AgentCard buildAgentCard(AgentCardDefinition agentCardDefinition) {
        AgentCapabilities capabilities = new AgentCapabilities.Builder()
                .streaming(agentCardDefinition.capabilities().streaming())
                .pushNotifications(agentCardDefinition.capabilities().pushNotifications())
                .build();

        List<AgentSkill> skills = agentCardDefinition.skills().stream()
                .map(skill -> new AgentSkill.Builder()
                        .id(skill.id())
                        .name(skill.name())
                        .description(skill.description())
                        .tags(skill.tags())
                        .build())
                .toList();

        String agentId = agentCardDefinition.id();

        String cardUri = UriComponentsBuilder.fromUriString(a2aBaseUri)
                .pathSegment(A2A)
                .pathSegment(agentId)
                .build()
                .toUriString();

        log.info("Card URI: {}", cardUri);

        return new AgentCard.Builder()
                .name(agentCardDefinition.name())
                .description(agentCardDefinition.description())
                .url(cardUri)
                .version(agentCardDefinition.version())
                .capabilities(capabilities)
                .defaultInputModes(agentCardDefinition.defaultInputModes())
                .defaultOutputModes(agentCardDefinition.defaultOutputModes())
                .skills(skills)
                .protocolVersion(agentCardDefinition.protocolVersion())
                .build();
    }
}
