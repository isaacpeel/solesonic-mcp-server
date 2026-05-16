package com.solesonic.a2a.api;

import com.solesonic.a2a.service.AgentCardService;
import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/a2a")
public class AgentCardController {
    private static final Logger log = LoggerFactory.getLogger(AgentCardController.class);

    private final AgentCardService agentCardService;

    public AgentCardController(AgentCardService agentCardService) {
        this.agentCardService = agentCardService;
    }

    @GetMapping(path = "/agents")
    public List<String> allAgentCardUris() {
        log.info("Getting all agent card URIs.");
        return agentCardService.allAgentCardUris();
    }

    @GetMapping(path = "/{agentId}/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard getAgentCard(@PathVariable String agentId) {
        log.info("Serving agent card: {}", agentId);
        return agentCardService.agentCardById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId));
    }

    @GetMapping(path = "/{agent}/card", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard getAgentCardV1(@PathVariable String agent) {
        log.info("Serving agent card via /sports/card: {}", agent);
        return getAgentCard(agent);
    }
}
