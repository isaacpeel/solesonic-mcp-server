package com.solesonic.mcp.prompt;

import com.solesonic.mcp.service.WeatherService;
import com.solesonic.mcp.tool.atlassian.AssigneeUserTools;
import com.solesonic.mcp.tool.atlassian.CreateConfluenceTools;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import com.solesonic.mcp.tool.atlassian.JiraIssueTools;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.solesonic.mcp.tool.SolesonicTool.availableTools;

@SuppressWarnings("unused")
@Service
public class PromptProvider {
    private static final Logger log =  LoggerFactory.getLogger(PromptProvider.class);

    private static final String AGENT_NAME = "agentName";
    private static final String USER_MESSAGE = "userMessage";
    private static final String INPUT = "input";
    public static final String AVAILABLE_TOOLS = "available_tools";

    @Value("classpath:prompt/basic-prompt.st")
    private Resource basicPrompt;

    @Value("classpath:prompt/jira_agile_prompt.st")
    private Resource jiraAgilePrompt;

    @Value("classpath:prompt/create_confluence_page_prompt.st")
    private Resource createConfluencePagePrompt;

    @Value("classpath:prompt/create_jira_issue_prompt.st")
    private Resource createJiraIssuePrompt;
    
    private static final String BASIC_PROMPT_DESCRIPTION = """
            A general-purpose assistant prompt for any topic that is not clearly about Jira or Confluence. Use this when the user is asking for explanations,
            brainstorming, coding help, writing, troubleshooting, planning, or casual conversation that does not primarily involve Jira issues, Jira boards, or
            Confluence pages.
            
            Example suitable requests:
            - “Explain how OAuth2 works.”
            - “Help me refactor this Java method.”
            - “Draft an email to my team.”
            - “Brainstorm product ideas for a habit-tracking app.”
            
            Do not use this prompt when the user is clearly asking to create or modify Jira issues, analyze Jira agile boards,
            or create Confluence pages. In those cases, prefer the more specific Jira or Confluence prompts.
            """;

    private static final String JIRA_AGILE_BOARD_PROMPT_DESCRIPTION = """
            A specialized prompt for analyzing and managing Jira agile boards (Scrum or Kanban). Use this when the user’s
            primary intent is to understand, summarize, or manage the state of a Jira board, sprint, or backlog, especially
            when they mention a Jira board, sprint, or board ID. The agent is focused on reading board data, understanding
            issue distribution, surfacing blockers, and helping with agile workflows.

            Typical use cases:
            - “Show me the current status of our Sprint 5 board.”
            - “Summarize the in-progress work on board 123.”
            - “Identify bottlenecks and blocked issues on this Jira board.”
            - “Help me plan the next sprint using the issues on board 42.”
            - "Show me all issues in board 1."

            This prompt is appropriate when the agent should use a Jira board ID and tools like `get_jira_board_issues`
            to inspect or reason about the content of a board.

            Do not use this prompt when the user is mainly asking to create a new Jira issue from scratch (e.g., “Create a
            bug ticket for login failures”) or to write a user story; in those cases, prefer the Jira issue creation prompt.
            """;

    private static final String CREATE_CONFLUENCE_PAGE_PROMPT_DESCRIPTION = """
            A specialized prompt for drafting and creating Confluence pages based on user requests. Use this when the user
            wants to create, structure, or generate content for a Confluence page, including technical documentation, runbooks,
            design specs, meeting notes, or project documentation. The agent will analyze the user’s request, structure the
            content with headings and sections, and use the `create_confluence_page` tool to create the page.

            Typical use cases:
            - “Create a Confluence page documenting our new API endpoints.”
            - “Generate a runbook page for handling production incidents.”
            - “Create a design spec page for the new authentication flow.”
            - “Turn this meeting summary into a Confluence page in the backend team space.”

            This prompt is appropriate when the final output should be a Confluence page with a clear title, structure, and content.

            Do not use this prompt when the user is just asking for a short explanation or text snippet that is not intended
            to live as a Confluence page, or when they are clearly working with Jira tickets instead of documentation.
            """;

    private static final String CREATE_JIRA_ISSUE_PROMPT_DESCRIPTION = """
            A specialized prompt for creating well-structured Jira issues based on natural language user requests. Use this
            when the user asks to create a new Jira ticket (story, task, bug, epic, etc.) or user story, especially when
            they provide requirements, scenarios, or acceptance criteria. The agent will extract a clear summary, write
            a detailed description (often in user-story format), derive acceptance criteria, handle assignee resolution
            with `assignee_id_lookup`, and create the issue using `create_jira_issue`.

            Typical use cases:
            - “Create a Jira ticket for a login authentication bug with these details…”
            - “Write a user story for password reset and create it in Jira.”
            - “Create a Jira task assigned to john.doe to add metrics to the billing service.”
            - “Turn these requirements into a Jira story with acceptance criteria.”

            This prompt is appropriate when the goal is to end up with a new Jira issue in the system, including
            optional assignee and acceptance criteria.

            Do not use this prompt when the user only wants to analyze or summarize existing Jira boards or
            sprints without creating new issues; in those cases, prefer the Jira agile board prompt.
            Also do not use it for non-Jira general tasks or for creating Confluence pages.
            """;

    @McpPrompt(name = "basic-prompt", 
              title = "General Assistant",
              description = BASIC_PROMPT_DESCRIPTION)
    public McpSchema.GetPromptResult basicPrompt(@McpArg(name="userMessage", description = "A message from the user to embed into this prompt.") String userMessage,
                                                 @McpArg(name = "agentName", description = "The name of the agent the user is interacting with.") String agentName) {
        log.info("Getting basic prompt.");

        String availableTools = availableTools(WeatherService.class);

        Map<String, Object> promptContext = Map.of(
                AGENT_NAME, agentName,
                USER_MESSAGE, userMessage,
                AVAILABLE_TOOLS, availableTools
        );

        return buildPromptResult("basic-prompt", this.basicPrompt, promptContext);
    }

    @McpPrompt(
            name = "jira-agile-board-prompt",
            title = "Jira Agile Board Analysis",
            description = JIRA_AGILE_BOARD_PROMPT_DESCRIPTION
    )
    public McpSchema.GetPromptResult jiraAgileBoardPrompt(
            @McpArg(name = "userMessage", description = "The user’s natural language request describing what they want to know or do with a Jira board.") String userMessage,
            @McpArg(name = "agentName", description = "The name of the agent the user is interacting with.") String agentName
    ) {
        log.info("Getting Jira agile board prompt.");

        String availableTools = availableTools(JiraAgileTools.class);

        Map<String, Object> templateVariables = Map.of(
                AGENT_NAME, agentName,
                INPUT, userMessage,
                AVAILABLE_TOOLS, availableTools
        );

        return buildPromptResult("jira-agile-board-prompt", this.jiraAgilePrompt, templateVariables);
    }

    @McpPrompt(
            name = "create-confluence-page-prompt",
            title = "Create Confluence Page",
            description = CREATE_CONFLUENCE_PAGE_PROMPT_DESCRIPTION
    )
    public McpSchema.GetPromptResult createConfluencePagePrompt(
            @McpArg(name = "userMessage", description = "The user’s natural language request describing the page to create in Confluence.") String userMessage,
            @McpArg(name = "agentName", description = "The name of the agent the user is interacting with.") String agentName
    ) {
        log.info("Getting Confluence page creation prompt.");

        String availableTools = availableTools(CreateConfluenceTools.class);

        Map<String, Object> templateVariables = Map.of(
                AGENT_NAME, agentName,
                INPUT, userMessage,
                AVAILABLE_TOOLS, availableTools
        );

        return buildPromptResult("create-confluence-page-prompt", this.createConfluencePagePrompt, templateVariables);
    }

    @McpPrompt(
            name = "create-jira-issue-prompt",
            title = "Create Jira Issue",
            description = CREATE_JIRA_ISSUE_PROMPT_DESCRIPTION
    )
    public McpSchema.GetPromptResult createJiraIssuePrompt(
            @McpArg(name = "userMessage", description = "The user’s natural language request describing the issue to create in Jira.") String userMessage,
            @McpArg(name = "agentName", description = "The name of the agent the user is interacting with.") String agentName
    ) {
        log.info("Getting Jira issue creation prompt.");

        String availableTools = availableTools(JiraIssueTools.class, AssigneeUserTools.class);

        Map<String, Object> templateVariables = Map.of(
                AGENT_NAME, agentName,
                INPUT, userMessage,
                AVAILABLE_TOOLS, availableTools
        );

        return buildPromptResult("create-jira-issue-prompt", this.createJiraIssuePrompt, templateVariables);
    }

    private McpSchema.GetPromptResult buildPromptResult(
            String promptName,
            Resource templateResource,
            Map<String, Object> templateVariables
    ) {
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .resource(templateResource)
                .variables(templateVariables)
                .build();

        Prompt prompt = promptTemplate.create();

        UserMessage promptUserMessage = prompt.getUserMessage();
        String promptText = promptUserMessage.getText();

        McpSchema.TextContent textContent = new McpSchema.TextContent(promptText);
        McpSchema.PromptMessage promptMessage = new McpSchema.PromptMessage(McpSchema.Role.USER, textContent);

        return new McpSchema.GetPromptResult(promptName, List.of(promptMessage));
    }
}
