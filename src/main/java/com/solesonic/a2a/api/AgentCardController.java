package com.solesonic.a2a.api;

import com.solesonic.a2a.service.AgentCardService;
import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/a2a")
public class AgentCardController {
    private static final Logger log = LoggerFactory.getLogger(AgentCardController.class);

    private final AgentCardService agentCardService;

    public AgentCardController(AgentCardService agentCardService1, AgentCardService agentCardService) {
        this.agentCardService = agentCardService1;
    }

    @GetMapping(path = "/agents")
    public List<String> allAgentCardUris() {
        return agentCardService.allAgentCards().stream()
                .map(agentCard->agentCard.url()+"/.well-known/agent.json")
                .toList();
    }

    @GetMapping(path = "/{agent}/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard getAgentCard(@PathVariable String agent) {
        log.debug("Serving agent card: {}", agent);
        return agentCardService.agentCardByName(agent);
    }

    @GetMapping(path = "/{agent}/card", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard getAgentCardV1(@PathVariable String agent) {
        log.debug("Serving agent card via /sports/card: {}", agent);
        return getAgentCard(agent);
    }
}
