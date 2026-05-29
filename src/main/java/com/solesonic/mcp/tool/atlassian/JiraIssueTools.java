package com.solesonic.mcp.tool.atlassian;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.agent.jira.JiraGraphConfig;
import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.model.JiraIssueCreatePayload;
import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import com.solesonic.mcp.service.atlassian.JiraIssueService;
import com.solesonic.mcp.tool.McpConfirmations;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.bsc.langgraph4j.GraphDefinition.END;

@SuppressWarnings("unused")
@Service
public class JiraIssueTools {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueTools.class);
    public static final String DELETE_JIRA_ISSUE = "delete_jira_issue";
    public static final String GET_JIRA_ISSUE = "get_jira_issue";
    public static final String CREATE_JIRA_STORY = "create_jira_story";
    public static final String CHAT_ID = "chatId";

    private static final String CREATE_JIRA_STORY_DESCRIPTION = """
            A guided workflow that generates a complete Jira story from a natural language description.
            Produces a summary, detailed description, acceptance criteria, and resolves the assignee before creating the issue.
            """;

    private final JiraIssueService jiraIssueService;
    private final CompiledGraph<JiraState> jiraCreateGraph;

    @Value("${jira.url.template}")
    private String jiraUrlTemplate;

    public JiraIssueTools(JiraIssueService jiraIssueService, CompiledGraph<JiraState> jiraCreateGraph) {
        this.jiraIssueService = jiraIssueService;
        this.jiraCreateGraph = jiraCreateGraph;
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

    @PreAuthorize("hasAuthority('ROLE_MCP-JIRA-CREATE')")
    @McpTool(name = CREATE_JIRA_STORY, description = CREATE_JIRA_STORY_DESCRIPTION)
    public String createJiraStory(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = "A natural language description of the story to create, including any relevant context such as the desired assignee.") String userMessage
    ) {
        String conversationId = extractConversationId(mcpSyncRequestContext);

        ProgressReporter progressReporter = new ProgressReporter(mcpSyncRequestContext);
        progressReporter.emit(5, "Starting Jira story workflow…");

        Map<String, Object> graphInput = Map.of(
                JiraState.USER_MESSAGE, userMessage,
                JiraState.CONVERSATION_ID, conversationId
        );

        AtomicReference<JiraState> finalStateRef = new AtomicReference<>();

        jiraCreateGraph.stream(graphInput, RunnableConfig.builder().build())
                .forEachAsync(output -> {
                    finalStateRef.set(output.state());

                    int progressPercent = switch (output.node()) {
                        case JiraGraphConfig.GENERATE_DETAILED_DESCRIPTION -> 20;
                        case JiraGraphConfig.GENERATE_STORY_SUMMARY        -> 40;
                        case JiraGraphConfig.GENERATE_ACCEPTANCE_CRITERIA  -> 60;
                        case JiraGraphConfig.RESOLVE_ASSIGNEE              -> 75;
                        case JiraGraphConfig.ASSEMBLE_PAYLOAD              -> 90;
                        case END                                           -> 100;
                        default                                            -> 10;
                    };
                    progressReporter.emit(progressPercent, "Completed: " + output.node());
                })
                .join();

        JiraState finalState = finalStateRef.get();

        if (finalState.assigneeNotResolved().orElse(false)) {
            return "Could not resolve an assignee from the description. Please clarify who the story should be assigned to and try again.";
        }

        JiraIssueCreatePayload payload = finalState.finalPayload().orElseThrow(
                () -> new IllegalStateException("Graph completed without assembling a Jira payload"));

        JiraIssue createdIssue = jiraIssueService.create(jiraIssueService.convert(payload));
        String issueKey = createdIssue.key();

        log.info("Jira story created: {}", issueKey);

        return "Created Jira story %s: %s".formatted(issueKey, jiraUrlTemplate.formatted(issueKey));
    }

    private static String extractConversationId(McpSyncRequestContext mcpSyncRequestContext) {
        Map<String, Object> meta = mcpSyncRequestContext.requestMeta();

        if (meta != null && meta.containsKey(CHAT_ID)) {
            return meta.get(CHAT_ID).toString();
        }

        return UUID.randomUUID().toString();
    }
}
