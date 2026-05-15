package com.solesonic.a2a.agent;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CreateJiraAgentCardConfig {

    @Bean(name = "createJiraAgentCard")
    public AgentCard createJiraAgentCard(@Value("${solesonic.jira-agent.url}") String agentBaseUrl) {

        AgentCapabilities agentCapabilities = new AgentCapabilities.Builder()
                .streaming(true)
                .pushNotifications(true)
                .build();

        AgentSkill createJiraSkill = new AgentSkill.Builder()
                .id("create-jira-issue")
                .name("Create Jira Issue Agent")
                .description("Generate a Jira issue from a natural language request")
                .tags(List.of("jira", "issue", "story"))
                .build();

        return new AgentCard.Builder()
                .name("create-jira-agent")
                .description("Generates a structured Jira issue payload from a natural language user story request")
                .url(agentBaseUrl + "/jira/a2a")
                .version("1.0.0")
                .capabilities(agentCapabilities)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(createJiraSkill))
                .protocolVersion("0.3.0")
                .build();
    }
}
