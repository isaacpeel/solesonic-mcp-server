package com.solesonic.mcp.tool.atlassian;

import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import com.solesonic.mcp.service.atlassian.JiraIssueService;
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
    public static final String DELETE_JIRA_ISSUE = "delete_jira_issue";
    public static final String GET_JIRA_ISSUE = "get_jira_issue";
    public static final String CHAT_ID = "chatId";

    private final JiraIssueService jiraIssueService;

    @Value("${jira.url.template}")
    private String jiraUrlTemplate;

    public JiraIssueTools(JiraIssueService jiraIssueService) {
        this.jiraIssueService = jiraIssueService;
    }

    public record CreateJiraRequest(String summary, String description, List<String> acceptanceCriteria, String assigneeId) {
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
