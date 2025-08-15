package com.solesonic.mcp.jira.config;

import com.solesonic.mcp.jira.service.JiraTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "mcp.jira.enabled", havingValue = "true")
public class JiraToolsConfig {

    @Bean
    public ToolCallbackProvider jiraToolsProvider(JiraTools jiraTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(jiraTools)
                .build();
    }
}
