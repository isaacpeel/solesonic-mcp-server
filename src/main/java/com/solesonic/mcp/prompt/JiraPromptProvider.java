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

    private static final String DESCRIPTION = "Create well-structured Jira issues from natural language requests.";

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
