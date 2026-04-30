package com.solesonic.mcp.prompt;

import com.solesonic.mcp.command.CreateJiraCommandProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptUtil.buildPromptResult;
import static com.solesonic.mcp.tool.atlassian.JiraIssueTools.CREATE_JIRA_ISSUE;

@SuppressWarnings("unused")
@Service
public class JiraPromptProvider {
    private static final Logger log = LoggerFactory.getLogger(JiraPromptProvider.class);

    private static final String INPUT = "input";
    private static final String AVAILABLE_TOOLS = "available_tools";

    private static final String DESCRIPTION = """
            A specialized prompt for creating well-structured Jira issues based on natural language user requests. Use this
            when the user asks to create a new Jira ticket (story, task, bug, epic, etc.) or user story, especially when
            they provide requirements, scenarios, or acceptance criteria. The agent will extract a clear summary, write
            a detailed description (often in user-story format), derive acceptance criteria, and create the issue using `create_jira_issue`.

            Typical use cases:
            - "Create a Jira ticket for a login authentication bug with these details…"
            - "Write a user story for password reset and create it in Jira."
            - "Create a Jira task assigned to john.doe to add metrics to the billing service."
            - "Turn these requirements into a Jira story with acceptance criteria."

            This prompt is appropriate when the goal is to end up with a new Jira issue in the system, including
            optional assignee and acceptance criteria.

            Do not use this prompt when the user only wants to analyze or summarize existing Jira boards or
            sprints without creating new issues; in those cases, prefer the Jira agile board prompt.
            Also do not use it for non-Jira general tasks or for creating Confluence pages.
            """;

    @Value("classpath:prompt/create_jira_issue_prompt.st")
    private Resource createJiraIssuePrompt;

    @McpPrompt(
            name = "create-jira-issue-prompt",
            title = "Create Jira Issue",
            description = DESCRIPTION,
            metaProvider = CreateJiraCommandProvider.class
    )
    public McpSchema.GetPromptResult createJiraIssuePrompt(
            @McpArg(name = "userMessage", description = "The user's natural language request describing the issue to create in Jira.") String userMessage
    ) {
        log.info("Getting Jira issue creation prompt.");

        String availableToolsList = CREATE_JIRA_ISSUE;

        log.info("Available tools: {}", availableToolsList);

        Map<String, Object> templateVariables = Map.of(
                INPUT, userMessage,
                AVAILABLE_TOOLS, availableToolsList
        );

        return buildPromptResult("create-jira-issue-prompt", this.createJiraIssuePrompt, templateVariables);
    }
}
