package com.solesonic.a2a.api;

import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.solesonic.a2a.agent.SportsAgentCardConfig.SPORTS_AGENT_CARD;

@RestController("/a2a")
public class SportsAgentController {

    private static final Logger log = LoggerFactory.getLogger(SportsAgentController.class);

    private final AgentCard agentCard;

    public SportsAgentController(@Qualifier(SPORTS_AGENT_CARD) AgentCard agentCard) {
        this.agentCard = agentCard;
    }

    @GetMapping(path = "/sports/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard getAgentCard() {
        log.debug("Serving agent card: {}", this.agentCard.name());
        return this.agentCard;
    }

    @GetMapping(path = "/sports/card", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard getAgentCardV1() {
        log.debug("Serving agent card via /sports/card: {}", this.agentCard.name());
        return this.agentCard;
    }
}
