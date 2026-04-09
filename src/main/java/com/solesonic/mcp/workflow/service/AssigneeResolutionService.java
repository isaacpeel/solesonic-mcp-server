package com.solesonic.mcp.workflow.service;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.model.atlassian.jira.User;
import com.solesonic.mcp.service.atlassian.JiraUserService;
import com.solesonic.mcp.workflow.model.AssigneeLookupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AssigneeResolutionService {
    private static final Logger log = LoggerFactory.getLogger(AssigneeResolutionService.class);

    private static final String INPUT = "input";

    private final JiraUserService jiraUserService;
    private final ChatClient chatClient;

    @Value("classpath:prompt/jira_assignee_lookup.st")
    private Resource jiraAssigneeLookupPrompt;

    public AssigneeResolutionService(JiraUserService jiraUserService, ChatClient chatClient) {
        this.jiraUserService = jiraUserService;
        this.chatClient = chatClient;
    }

    public AssigneeLookupResult resolve(String userRequest, McpSyncRequestContext mcpSyncRequestContext) {
        log.info("resolve AssigneeResolutionService");
        mcpSyncRequestContext.progress(p -> p.percentage(50).message("Looking up assignee"));

        PromptTemplate assigneeLookupTemplate = new PromptTemplate(jiraAssigneeLookupPrompt);

        Map<String, Object> inputs = Map.of(INPUT, userRequest);
        Prompt assigneeLookup = assigneeLookupTemplate.create(inputs);

        String assigneeToLookup = chatClient.prompt(assigneeLookup).call().content();

        User user = jiraUserService.search(assigneeToLookup)
                .stream()
                .findFirst()
                .orElseThrow(() -> new JiraException("Assignee lookup failed for required assignee: " + assigneeToLookup));

        log.info("Found assignee: {}", user.displayName());
        mcpSyncRequestContext.progress(p -> p.percentage(5).message("Found up assignee: "+user.displayName()));

        return new AssigneeLookupResult(true, user.accountId(), "RESOLVED");
    }
}
