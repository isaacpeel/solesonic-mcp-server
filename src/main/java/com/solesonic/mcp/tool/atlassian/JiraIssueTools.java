package com.solesonic.mcp.tool.atlassian;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.agent.jira.JiraGraphConfig;
import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.model.AssigneeCandidate;
import com.solesonic.agent.model.JiraIssueCreatePayload;
import com.solesonic.model.atlassian.jira.JiraIssue;
import com.solesonic.service.atlassian.JiraIssueService;
import com.solesonic.mcp.tool.McpConfirmations;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
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
import static org.bsc.langgraph4j.GraphDefinition.START;

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
            If the assignee cannot be determined, the user is asked to pick one; the story is not created without an assignee.
            """;

    private final JiraIssueService jiraIssueService;
    private final CompiledGraph<JiraState> jiraCreateGraph;
    private final JiraAssigneeElicitation jiraAssigneeElicitation;
    private final MemorySaver jiraAssigneeCheckpointSaver;

    @Value("${jira.url.template}")
    private String jiraUrlTemplate;

    public JiraIssueTools(JiraIssueService jiraIssueService,
                          CompiledGraph<JiraState> jiraCreateGraph,
                          JiraAssigneeElicitation jiraAssigneeElicitation,
                          MemorySaver jiraAssigneeCheckpointSaver) {
        this.jiraIssueService = jiraIssueService;
        this.jiraCreateGraph = jiraCreateGraph;
        this.jiraAssigneeElicitation = jiraAssigneeElicitation;
        this.jiraAssigneeCheckpointSaver = jiraAssigneeCheckpointSaver;
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
        log.info("Create jira tool called.");
        String conversationId = extractConversationId(mcpSyncRequestContext);

        ProgressReporter progressReporter = new ProgressReporter(mcpSyncRequestContext);
        progressReporter.emit(5, "Starting Jira story workflow…");

        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
                .build();

        boolean resumingInFlightRequest = jiraAssigneeCheckpointSaver.get(config).isPresent();

        GraphInput input = resumingInFlightRequest
                ? GraphInput.resume(Map.of())
                : GraphInput.args(Map.of(
                        JiraState.USER_MESSAGE, userMessage,
                        JiraState.CONVERSATION_ID, conversationId
                ));

        NodeOutput<JiraState> output = runGraph(input, config, progressReporter);

        if (!output.isEND()) {
            progressReporter.emit(80, "Waiting for an assignee to be chosen…");

            JiraState pausedState = output.state();
            List<AssigneeCandidate> matchingCandidates = pausedState.assigneeCandidates().orElse(List.of());

            JiraAssigneeElicitation.Selection selection = jiraAssigneeElicitation.selectAssignee(
                    mcpSyncRequestContext, matchingCandidates, Map.of(CHAT_ID, conversationId));

            switch (selection) {
                case JiraAssigneeElicitation.Selection.Selected selected -> {
                    Map<String, Object> resumeData = Map.of(
                            JiraState.ASSIGNEE_LOOKUP_RESULT, selected.assigneeLookupResult(),
                            JiraState.ASSIGNEE_NOT_RESOLVED, false
                    );
                    output = runGraph(GraphInput.resume(resumeData), config, progressReporter);
                }
                case JiraAssigneeElicitation.Selection.Declined _ -> {
                    releaseCheckpoint(config);
                    return "Story not created: Jira requires an assignee and none was selected.";
                }
                case JiraAssigneeElicitation.Selection.Cancelled _ -> {
                    releaseCheckpoint(config);
                    return "Story creation cancelled.";
                }
                case JiraAssigneeElicitation.Selection.NoCandidates _ -> {
                    releaseCheckpoint(config);
                    return "Story not created: Jira returned no assignable users for the project, and Jira requires an assignee.";
                }
                case JiraAssigneeElicitation.Selection.InvalidSelection _ -> {
                    releaseCheckpoint(config);
                    return "Story not created: the selected assignee is not an assignable Jira user.";
                }
            }
        }

        JiraState finalState = output.state();

        JiraIssueCreatePayload payload = finalState.finalPayload().orElseThrow(
                () -> new IllegalStateException("Graph completed without assembling a Jira payload"));

        JiraIssue createdIssue = jiraIssueService.create(jiraIssueService.convert(payload));
        String issueKey = createdIssue.key();

        log.info("Jira story created: {}", issueKey);

        return "Created Jira story %s: %s".formatted(issueKey, jiraUrlTemplate.replace("{key}", issueKey));
    }

    /**
     * Streams the graph to completion (either a fresh run or a resume), reporting progress along
     * the way, and returns the last emitted output. That output is either the natural end of the
     * graph or, per {@link NodeOutput#isEND()}, the point where {@code AwaitAssigneeSelectionNode}
     * paused it.
     */
    private NodeOutput<JiraState> runGraph(GraphInput input, RunnableConfig config, ProgressReporter progressReporter) {
        AtomicReference<NodeOutput<JiraState>> finalOutputRef = new AtomicReference<>();

        jiraCreateGraph.stream(input, config)
                .forEachAsync(output -> {
                    finalOutputRef.set(output);

                    int progressPercent = switch (output.node()) {
                        case JiraGraphConfig.GENERATE_CONTENT_AND_RESOLVE_ASSIGNEE -> 60;
                        case JiraGraphConfig.AWAIT_ASSIGNEE_SELECTION              -> 80;
                        case JiraGraphConfig.ASSEMBLE_PAYLOAD                      -> 90;
                        case END                                                   -> 100;
                        default                                                    -> 10;
                    };

                    String node = output.node();

                    switch(node) {
                        case START -> node = "Jira create agent started.";
                        case END -> node = "Jira create agent finished.";
                        default -> node = "Completed: " + node;
                    }

                    progressReporter.emit(progressPercent, node);
                })
                .join();

        return finalOutputRef.get();
    }

    /**
     * Discards the checkpoint for a conversation that ended without an assignee, so a later,
     * unrelated story request in the same conversation doesn't resume this abandoned attempt's
     * already-generated content.
     */
    private void releaseCheckpoint(RunnableConfig config) {
        try {
            jiraAssigneeCheckpointSaver.release(config);
        } catch (Exception exception) {
            log.warn("Failed to release the Jira story checkpoint for this conversation", exception);
        }
    }

    private static String extractConversationId(McpSyncRequestContext mcpSyncRequestContext) {
        Map<String, Object> meta = mcpSyncRequestContext.requestMeta();

        if (meta != null && meta.containsKey(CHAT_ID)) {
            return meta.get(CHAT_ID).toString();
        }

        return UUID.randomUUID().toString();
    }
}
