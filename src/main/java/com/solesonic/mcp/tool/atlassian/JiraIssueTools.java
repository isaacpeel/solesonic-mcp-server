package com.solesonic.mcp.tool.atlassian;

import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import com.solesonic.mcp.service.atlassian.JiraIssueService;
import com.solesonic.mcp.tool.provider.CreateJiraMetaProvider;
import com.solesonic.mcp.workflow.CreateJiraWorkflow;
import com.solesonic.mcp.tool.McpConfirmations;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public record CreateJiraRequest(String summary, String description, List<String> acceptanceCriteria, String assigneeId) {
    }

    @SuppressWarnings("unused")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-CREATE')")
    @McpTool(name = CREATE_JIRA_ISSUE, description = "Workflow to create a jira issue.", metaProvider = CreateJiraMetaProvider.class)
    public String createJiraWorkflow(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = "The users request.") String userMessage
    ) {
        var jiraIssueCreatePayload = createJiraWorkflow.startWorkflow(mcpSyncRequestContext, userMessage);

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
        JiraIssue created = jiraIssueService.create(jiraIssue);

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
    }

    @SuppressWarnings("unused")
    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-DELETE')")
    @McpTool(name = DELETE_JIRA_ISSUE, description = "Deletes a jira issue by its ID.")
    public String deleteJiraIssue(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = "The key or id of the jira issue to delete.") String keyOrIssueId
    ) {
        mcpSyncRequestContext.log(logging -> logging.message("Delete Jira Issue Tool Started for: " + keyOrIssueId));
        log.info("Delete request for jira issue: {}", keyOrIssueId);

        Map<String, Object> toolContext = mcpSyncRequestContext.requestMeta();

        String chatId;
        if (toolContext != null && toolContext.containsKey(CHAT_ID)) {
            chatId = toolContext.get(CHAT_ID).toString();
            log.info("Chat ID from ToolContext: {}", chatId);
        } else {
            chatId = UUID.randomUUID().toString();
        }

        log.info("Prompting user to confirm deletion of Jira issue: {}", keyOrIssueId);

        ElicitResult elicitResult = McpConfirmations.confirm(
                mcpSyncRequestContext,
                "Are you sure you want to delete Jira issue: " + keyOrIssueId + "?",
                Map.of(CHAT_ID, chatId)
        );

        ElicitResult.Action action = elicitResult.action();

        log.info("Elicitation action: {}", action);

        return switch (action) {
            case ACCEPT -> {
                jiraIssueService.delete(keyOrIssueId);
                mcpSyncRequestContext.log(logging -> logging.message("Successfully deleted Jira issue: " + keyOrIssueId));
                yield "Successfully deleted Jira Issue: " + keyOrIssueId;
            }
            case DECLINE -> {
                log.info("Deletion declined by user for: {}", keyOrIssueId);
                mcpSyncRequestContext.log(logging -> logging.message("Deletion declined by user for: " + keyOrIssueId));
                yield "Deletion declined for issue: " + keyOrIssueId;
            }
            case CANCEL -> {
                log.info("Deletion cancelled by user for: {}", keyOrIssueId);
                mcpSyncRequestContext.log(logging -> logging.message("Deletion cancelled by user for: " + keyOrIssueId));
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
