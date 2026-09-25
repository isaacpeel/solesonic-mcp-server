package com.solesonic.agent.jira;

import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Checkpoint saver backing {@link JiraGraphConfig}'s create-story graph. In-process is sufficient
 * here: the checkpoint only needs to survive across separate {@code create_jira_story} tool
 * invocations for the same conversation while the server keeps running, not across restarts.
 */
@Configuration
public class JiraCheckpointConfig {

    @Bean
    public MemorySaver jiraAssigneeCheckpointSaver() {
        return new MemorySaver();
    }
}
