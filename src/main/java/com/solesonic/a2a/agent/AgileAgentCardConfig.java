package com.solesonic.a2a.agent;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AgileAgentCardConfig {

    public static final String AGILE_AGENT_CARD = "agileAgentCard";

    @Bean(name = AGILE_AGENT_CARD)
    public AgentCard agileAgentCard(@Value("${solesonic.agile-agent.url}") String agentBaseUrl) {

        AgentCapabilities agentCapabilities = new AgentCapabilities.Builder()
                .streaming(true)
                .pushNotifications(true)
                .build();

        AgentSkill agileSkill = new AgentSkill.Builder()
                .id("agile-query")
                .name("Agile Query Agent")
                .description("Parse and plan natural language Jira board queries")
                .tags(List.of("jira", "agile", "sprint"))
                .build();

        return new AgentCard.Builder()
                .name("agile-query-agent")
                .description("Parses natural language Jira board queries into structured JQL filters and query plans")
                .url(agentBaseUrl + "/agile/a2a")
                .version("1.0.0")
                .capabilities(agentCapabilities)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(agileSkill))
                .protocolVersion("0.3.0")
                .build();
    }
}
