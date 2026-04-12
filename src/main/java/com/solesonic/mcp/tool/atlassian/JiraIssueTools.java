package com.solesonic.mcp.tool.atlassian;

import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import com.solesonic.mcp.service.atlassian.JiraIssueService;
import com.solesonic.mcp.tool.provider.DirectReturnMetaProvider;
import com.solesonic.mcp.workflow.CreateJiraWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tools service for Jira operations.
 * Provides jiraUserName lookup and issue creation functionality.
 */
@SuppressWarnings("unused")
@Service
public class JiraIssueTools {


    private static final Logger log = LoggerFactory.getLogger(JiraIssueTools.class);
    public static final String CREATE_JIRA_ISSUE = "create_jira_issue";
    public static final String DELETE_JIRA_ISSUE = "delete_jira_issue";
    public static final String GET_JIRA_ISSUE = "get_jira_issue";
    public static final String CHAT_ID = "chatId";

    public static final String JIRA_ISSUE_TEMPLATE = """
            ## Jira Issue Created
            
            **[%s](%s)**
            
            **Summary:** %s
            
            **Description:**
            %s
            
            **Acceptance Criteria:**
            %s
            **Assignee:** %s
            """;

    private final JiraIssueService jiraIssueService;
    private final CreateJiraWorkflow createJiraWorkflow;

    @Value("${jira.url.template}")
    private String jiraUrlTemplate;

    public JiraIssueTools(JiraIssueService jiraIssueService,
                          CreateJiraWorkflow createJiraWorkflow) {
        this.jiraIssueService = jiraIssueService;
        this.createJiraWorkflow = createJiraWorkflow;
    }

    public static final String CREATE_JIRA_ISSUE_DESCRIPTION = """
            A short, required title for the Jira issue (the “summary” field in Jira).
            - Must not be empty.
            - Should briefly describe the user story or task (for example: "Add password reset flow to login page").
            """;

    public static final String CREATE_JIRA_ISSUE_DESCRIPTION_DESCRIPTION = """
            A required, detailed description of the Jira issue.
            - Explain the background, goal, and any important details.
            - Include any context that will help the assignee understand what to implement or fix.
            - Do NOT leave this blank; always provide a meaningful description.
            """;

    public static final String CREATE_JIRA_ISSUE_ASSIGNEE_DESCRIPTION = """
            The Jira account ID of the user to assign this issue to.
            - This is NOT the display name or email.
            - Before calling this tool, if the user specified an assignee by name or email, first call the `jira_assignee_search` tool to look up the user.
            - Use the `accountId` returned by `jira_assignee_search` as this `assigneeId` value.
            - Do NOT guess or invent an ID; always use a real `accountId` from `jira_assignee_search`, or omit this only if the user explicitly requests an unassigned issue.
    """;

    public static final String CREATE_JIRA_ISSUE_ACCEPTANCE_CRITERIA_DESCRIPTION = """
            A required `String[]` of acceptance criteria for the Jira issue.
             - This field is MANDATORY. You must NOT send an empty list.
             - If the user did not provide specific criteria, you MUST generate at least 3 logical, testable conditions based on the description.
    """;

    public record CreateJiraRequest(
            @McpToolParam(description = "A summary of the jira issue, this is a short title for the user story.") String summary,
            @McpToolParam(description = "This is the main body of the jira ") String description,
            @McpToolParam(description = CREATE_JIRA_ISSUE_ACCEPTANCE_CRITERIA_DESCRIPTION) List<String> acceptanceCriteria,
            @McpToolParam(description = CREATE_JIRA_ISSUE_ASSIGNEE_DESCRIPTION) String assigneeId) {
    }

    public record DeleteConfirmation(boolean confirmed, String chatId) {}

    /**
     * Creates a new Jira issue with the provided details.
     *
     * @return CreateJiraIssueResponse with the created issue ID and URL
     */
    @SuppressWarnings("unused")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-CREATE')")
    @McpTool(name = CREATE_JIRA_ISSUE, description = "Workflow to create a jira issue.", metaProvider = DirectReturnMetaProvider.class)
    public Mono<String> createJiraWorkflow(
            McpAsyncRequestContext mcpAsyncRequestContext,
            @McpToolParam(description = "The users request.") String userMessage
    ) {
        return mcpAsyncRequestContext.progress(progress -> progress.percentage(0).message(""))
                .then(Mono.delay(Duration.ofMillis(300)))
                .then(Mono.defer(() -> createJiraWorkflow.startWorkflow(mcpAsyncRequestContext, userMessage)))
                .flatMap(jiraIssueCreatePayload -> {
                    CreateJiraRequest createJiraRequest = new CreateJiraRequest(
                            jiraIssueCreatePayload.summary(),
                            jiraIssueCreatePayload.description(),
                            jiraIssueCreatePayload.acceptanceCriteria(),
                            jiraIssueCreatePayload.assigneeLookupResult().assigneeId());

                    log.info("Invoking create jira tool");
                    log.debug("Summary: {}", createJiraRequest.summary);
                    log.debug("Description: {}", createJiraRequest.description);
                    log.debug("Assignee ID: {}", createJiraRequest.assigneeId);

                    JiraIssue jiraIssue = jiraIssueService.convert(jiraIssueCreatePayload);

                    return jiraIssueService.create(jiraIssue)
                            .map(created -> {
                                log.debug("Created jira issue: {}", created);
                                String jiraUri = jiraUrlTemplate.replace("{key}", created.key());
                                log.debug("Using jira uri: {}", jiraUri);

                                StringBuilder acceptanceCriteriaLines = new StringBuilder();
                                createJiraRequest.acceptanceCriteria().forEach(criterion ->
                                        acceptanceCriteriaLines.append("- ").append(criterion.strip().replace("\n", " ")).append("\n")
                                );

                                String assigneeDisplay = jiraIssueCreatePayload.assigneeLookupResult().assigneeName();

                                return JIRA_ISSUE_TEMPLATE.formatted(
                                        created.key(),
                                        jiraUri,
                                        createJiraRequest.summary(),
                                        createJiraRequest.description(),
                                        acceptanceCriteriaLines,
                                        assigneeDisplay
                                );
                            });
                });
    }

    @SuppressWarnings("unused")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-DELETE')")
    @McpTool(name = DELETE_JIRA_ISSUE, description = "Deletes a jira issue by its ID.")
    public Mono<String> deleteJiraIssue(
            McpAsyncRequestContext mcpAsyncRequestContext,
            @McpToolParam(description = "The key or id of the jira issue to delete.") String keyOrIssueId
    ) {

        log.info("Delete request for jira issue: {}", keyOrIssueId);

        Map<String, Object> toolContext = mcpAsyncRequestContext.requestMeta();

        String chatId;
        if (toolContext != null && toolContext.containsKey(CHAT_ID)) {
            chatId = toolContext.get(CHAT_ID).toString();
            log.info("Chat ID from ToolContext: {}", chatId);
        } else {
            chatId = UUID.randomUUID().toString();
        }

        String finalChatId = chatId;

        return mcpAsyncRequestContext.log(logging -> logging.message("Delete Jira Issue Tool Started for: " + keyOrIssueId))
                .then(mcpAsyncRequestContext.elicit(
                        elicit -> elicit
                                .message("Are you sure you want to delete Jira issue: " + keyOrIssueId + "?")
                                .meta(Map.of(CHAT_ID, finalChatId)),
                        DeleteConfirmation.class
                ))
                .map(StructuredElicitResult::action)
                .flatMap(action -> {
                    log.info("Elicitation action: {}", action);

                    return switch (action) {
                        case ACCEPT -> jiraIssueService.delete(keyOrIssueId)
                                .then(mcpAsyncRequestContext.log(logging -> logging.message("Successfully deleted Jira issue: " + keyOrIssueId)))
                                .thenReturn("Successfully deleted Jira Issue: " + keyOrIssueId);
                        case DECLINE -> {
                            log.info("Deletion declined by user for: {}", keyOrIssueId);

                            yield mcpAsyncRequestContext.log(logging -> logging.message("Deletion declined by user for: " + keyOrIssueId))
                                    .thenReturn("Deletion declined for issue: " + keyOrIssueId);
                        }
                        case CANCEL -> {
                            log.info("Deletion cancelled by user for: {}", keyOrIssueId);

                            yield mcpAsyncRequestContext.log(logging -> logging.message("Deletion cancelled by user for: " + keyOrIssueId))
                                    .thenReturn("Deletion canceled for issue: " + keyOrIssueId);
                        }
                    };
                });
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-GET')")
    @McpTool(name = GET_JIRA_ISSUE, description = "Gets a jira issue by its `id` or by it's `key`")
    public Mono<JiraIssue> get(String issueId) {
        log.info("Retrieving jira issue by ID: {}", issueId);

        return jiraIssueService.get(issueId);
    }
}