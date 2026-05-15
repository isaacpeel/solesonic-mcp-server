package com.solesonic.a2a.api;

import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SportsAgentController {

    private static final Logger log = LoggerFactory.getLogger(SportsAgentController.class);

    private final AgentCard agentCard;

    public SportsAgentController(AgentCard agentCard) {
        this.agentCard = agentCard;
    }

    /**
     * Returns agent card metadata.
     */
    @GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard getAgentCard() {
        log.debug("Serving agent card: {}", this.agentCard.name());
        return this.agentCard;
    }

    /**
     * Alternative endpoint for getting the agent card. Some A2A implementations may use
     * this endpoint.
     * @return the agent card in JSON format
     */
    @GetMapping(path = "/card", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard getAgentCardV1() {
        log.debug("Serving agent card via /card: {}", this.agentCard.name());
        return this.agentCard;
    }
}
