package com.solesonic.mcp.tool.atlassian;

import com.solesonic.mcp.model.atlassian.jira.*;
import com.solesonic.mcp.service.atlassian.JiraIssueService;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springaicommunity.mcp.context.StructuredElicitResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.solesonic.mcp.service.atlassian.AtlassianConstants.ISSUE_TYPE_ID;
import static com.solesonic.mcp.service.atlassian.AtlassianConstants.PROJECT_ID;
import static com.solesonic.mcp.tool.atlassian.AssigneeUserTools.ASSIGN_JIRA;

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

    private final JiraIssueService jiraIssueService;

    @Value("${jira.url.template}")
    private String jiraUrlTemplate;

    public JiraIssueTools(JiraIssueService jiraIssueService) {
        this.jiraIssueService = jiraIssueService;
    }

    public record CreateJiraResponse(String issueId, String issueUri) {
    }

    public record CreateJiraRequest(
            @McpToolParam(description = "A summary of the jira issue, this is a short title for the user story.") String summary,
            @McpToolParam(description = "This is the main body of the jira ") String description,
            List<String> acceptanceCriteria, String assigneeId) {
    }

    public record DeleteConfirmation(boolean confirmed, String chatId) {}

    /**
     * Creates a new Jira issue with the provided details.
     *
     * @return CreateJiraIssueResponse with the created issue ID and URL
     */
    @SuppressWarnings("unused")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-CREATE')")
    @McpTool(name = CREATE_JIRA_ISSUE, description = "Creates a jira issue.  Use responsibly and ensure no repeated calls for the same request.  If an jiraUserName is needed always call '" + ASSIGN_JIRA + "' first.")
    public CreateJiraResponse createJiraIssue(@McpToolParam(description = "Request to create a jira issue.  This includes a `description` of the user story, a list of its `acceptanceCriteria` and an `assigneeId") CreateJiraRequest createJiraRequest) {
        log.debug("Invoking create jira function");
        log.debug("Summary: {}", createJiraRequest.summary);
        log.debug("Description: {}", createJiraRequest.description);
        log.debug("Assignee ID: {}", createJiraRequest.assigneeId);

        TextContent descriptionText = TextContent.text(createJiraRequest.description())
                .type("text")
                .build();

        List<TextContent> acceptanceCriteria = new ArrayList<>();

        createJiraRequest.acceptanceCriteria().forEach(ac -> {
            TextContent acceptanceCriteriaItemTextContent = TextContent.type("text")
                    .text(ac)
                    .build();

            TextContent acceptanceCriteriaItemContent = TextContent.type("paragraph")
                    .content(List.of(acceptanceCriteriaItemTextContent))
                    .build();

            TextContent listItemContent = TextContent.type("listItem")
                    .content(List.of(acceptanceCriteriaItemContent))
                    .build();

            acceptanceCriteria.add(listItemContent);
        });

        Content bulletList = Content
                .content(acceptanceCriteria)
                .type("bulletList")
                .build();

        Content descriptionContent = Content.content(List.of(descriptionText))
                .type("paragraph")
                .build();

        TextContent acceptanceCriteriaHeader = TextContent.text("Acceptance Criteria:")
                .type("text")
                .build();

        Content acceptanceCriteriaContent = Content
                .content(List.of(acceptanceCriteriaHeader))
                .type("paragraph")
                .build();

        Description description = Description.content(List.of(descriptionContent, acceptanceCriteriaContent, bulletList))
                .type("doc")
                .version(1)
                .build();

        IssueType issueType = IssueType.id(ISSUE_TYPE_ID).build();

        Project project = Project.id(PROJECT_ID).build();

        User user = User.accountId(createJiraRequest.assigneeId()).build();

        Fields fields = Fields.summary(createJiraRequest.summary())
                .project(project)
                .description(description)
                .issuetype(issueType)
                .assignee(user)
                .build();

        JiraIssue jiraIssue = JiraIssue.fields(fields).build();

        JiraIssue created = jiraIssueService.create(jiraIssue);

        log.debug("Created jira issue: {}", created);

        String jiraUri = jiraUrlTemplate.replace("{key}", created.key());

        log.debug("Using jira uri: {}", jiraUri);

        return new CreateJiraResponse(created.id(), jiraUri);
    }

    @SuppressWarnings("unused")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-DELETE')")
    @McpTool(name = DELETE_JIRA_ISSUE, description = "Deletes a jira issue by its ID.")
    public String deleteJiraIssue(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = "The key or id of the jira issue to delete.") String keyOrIssueId
    ) {

        log.info("Delete request for jira issue: {}", keyOrIssueId);

        Map<String, Object> toolContext = mcpSyncRequestContext.requestMeta();

        String chatId;
        if(toolContext != null && toolContext.containsKey(CHAT_ID)) {
            chatId = toolContext.get(CHAT_ID).toString();
            log.info("Chat ID from ToolContext: {}", chatId);
        } else {
            chatId = UUID.randomUUID().toString();
        }

        mcpSyncRequestContext.log(logging -> logging.message("Delete Jira Issue Tool Started for: " + keyOrIssueId));

        String finalChatId = chatId;

        StructuredElicitResult<DeleteConfirmation> elicitation = mcpSyncRequestContext.elicit(
                elicit -> elicit
                        .message("Are you sure you want to delete Jira issue: " + keyOrIssueId + "?")
                        .meta(Map.of(CHAT_ID, finalChatId)), DeleteConfirmation.class);

        McpSchema.ElicitResult.Action action = elicitation.action();

        log.info("Elicitation action: {}", action);

        return switch (action) {
            case ACCEPT -> {
                jiraIssueService.delete(keyOrIssueId);

                mcpSyncRequestContext.log(logging -> logging.message("Successfully deleted Jira issue: " + keyOrIssueId));

                log.info("Successfully deleted Jira issue: {}", keyOrIssueId);

                yield "Successfully deleted Jira Issue: " + keyOrIssueId;
            }
            case DECLINE -> {
                mcpSyncRequestContext.log(logging -> logging.message("Deletion declined by user for: " + keyOrIssueId));

                log.info("Deletion declined by user for: {}", keyOrIssueId);

                yield "Deletion declined for issue: " + keyOrIssueId;
            }
            case CANCEL -> {
                mcpSyncRequestContext.log(logging -> logging.message("Deletion cancelled by user for: " + keyOrIssueId));

                log.info("Deletion cancelled by user for: {}", keyOrIssueId);

                yield "Deletion canceled for issue: " + keyOrIssueId;
            }
        };
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-GET')")
    @McpTool(name = GET_JIRA_ISSUE, description = "Gets a jira issue by its `id` or by it's `key`")
    public JiraIssue get(String issueId) {
        log.info("Retrieving jira issue by ID: {}", issueId);

        return jiraIssueService.get(issueId);
    }
}