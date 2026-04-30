package com.solesonic.mcp.prompt;

import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import com.solesonic.mcp.tool.atlassian.JiraIssueTools;
import com.solesonic.mcp.tool.general.DateTools;
import com.solesonic.mcp.tool.tavily.WebSearchTools;
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
import static com.solesonic.mcp.tool.SolesonicTool.availableTools;

@SuppressWarnings("unused")
@Service
public class AgilePromptProvider {
    private static final Logger log = LoggerFactory.getLogger(AgilePromptProvider.class);

    private static final String AGENT_NAME = "agentName";
    private static final String INPUT = "input";
    private static final String AVAILABLE_TOOLS = "available_tools";

    private static final String DESCRIPTION = """
            A specialized prompt for analyzing and managing Jira agile boards (Scrum or Kanban). Use this when the user's
            primary intent is to understand, summarize, or manage the state of a Jira board, sprint, or backlog, especially
            when they mention a Jira board, sprint, or board ID. The agent is focused on reading board data, understanding
            issue distribution, surfacing blockers, and helping with agile workflows.

            Typical use cases:
            - "Show me the current status of our Sprint 5 board."
            - "Summarize the in-progress work on board 123."
            - "Identify bottlenecks and blocked issues on this Jira board."
            - "Help me plan the next sprint using the issues on board 42."
            - "Show me all issues in board 1."

            This prompt is appropriate when the agent should use a Jira board ID and tools like `get_jira_board_issues`
            to inspect or reason about the content of a board.

            Do not use this prompt when the user is mainly asking to create a new Jira issue from scratch (e.g., "Create a
            bug ticket for login failures") or to write a user story; in those cases, prefer the Jira issue creation prompt.
            """;

    @Value("classpath:prompt/jira_agile_prompt.st")
    private Resource jiraAgilePrompt;

    @McpPrompt(
            name = "jira-agile-board-prompt",
            title = "Jira Agile Board Analysis",
            description = DESCRIPTION
    )
    public McpSchema.GetPromptResult jiraAgileBoardPrompt(
            @McpArg(name = "userMessage", description = "The user's natural language request describing what they want to know or do with a Jira board.") String userMessage,
            @McpArg(name = "agentName", description = "The name of the agent the user is interacting with.") String agentName
    ) {
        log.info("Getting Jira agile board prompt.");

        String availableToolsList = availableTools(JiraAgileTools.class, JiraIssueTools.class, WebSearchTools.class, DateTools.class);

        Map<String, Object> templateVariables = Map.of(
                AGENT_NAME, agentName,
                INPUT, userMessage,
                AVAILABLE_TOOLS, availableToolsList
        );

        return buildPromptResult("jira-agile-board-prompt", this.jiraAgilePrompt, templateVariables);
    }
}
