package com.solesonic.a2a.config;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SportsAgentConfiguration {

    @Bean
    public AgentCard sportsAgentCard(@Value("${solesonic.sports-agent.url}") String agentBaseUrl) {
        return new AgentCard.Builder()
                .name("nba-sports-ball")
                .description("Researches current NBA schedules, standings, players, trades, and injuries from live sources")
                .url(agentBaseUrl + "/a2a")
                .version("1.0.0")
                .capabilities(new AgentCapabilities.Builder()
                        .streaming(true)
                        .pushNotifications(true)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(new AgentSkill.Builder()
                        .id("nba-agent")
                        .name("NBA Agent")
                        .description("Research current NBA schedules, standings, players, trades, and injuries")
                        .tags(List.of("sports", "nba"))
                        .build()))
                .protocolVersion("0.3.0")
                .build();
    }
}
