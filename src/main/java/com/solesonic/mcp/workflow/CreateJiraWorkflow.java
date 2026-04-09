package com.solesonic.mcp.workflow;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.workflow.chain.UserStoryChainContext;
import com.solesonic.mcp.workflow.chain.UserStoryPromptChain;
import com.solesonic.mcp.workflow.model.AssigneeLookupResult;
import com.solesonic.mcp.workflow.model.JiraIssueCreatePayload;
import com.solesonic.mcp.workflow.service.AssigneeResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CreateJiraWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CreateJiraWorkflow.class);

    private static final String USER_STORY_TASK = "user-story-generation";
    private static final String ASSIGNEE_LOOKUP_TASK = "assignee-lookup";

    private final ParallelOrchestrator parallelOrchestrator;
    private final UserStoryPromptChain userStoryPromptChain;
    private final AssigneeResolutionService assigneeResolutionService;

    public CreateJiraWorkflow(
            ParallelOrchestrator parallelOrchestrator,
            UserStoryPromptChain userStoryPromptChain,
            AssigneeResolutionService assigneeResolutionService
    ) {
        this.parallelOrchestrator = parallelOrchestrator;
        this.userStoryPromptChain = userStoryPromptChain;
        this.assigneeResolutionService = assigneeResolutionService;
    }

    public JiraIssueCreatePayload startWorkflow(McpSyncRequestContext mcpSyncRequestContext, String userMessage) {
        try {
            Map<String, Object> taskResults = parallelOrchestrator.executeParallel(List.of(
                    new ParallelOrchestrator.AgentTask<>(
                            USER_STORY_TASK,
                            () -> {
                                mcpSyncRequestContext.progress(p -> p.percentage(1).message("Generating user story"));

                                return userStoryPromptChain.run(userMessage, mcpSyncRequestContext);
                            }
                    ),
                    new ParallelOrchestrator.AgentTask<>(
                            ASSIGNEE_LOOKUP_TASK,
                            () -> assigneeResolutionService.resolve(userMessage, mcpSyncRequestContext)
                    )
            ));

            UserStoryChainContext userStoryResult = (UserStoryChainContext) taskResults.get(USER_STORY_TASK);
            AssigneeLookupResult assigneeLookupResult = (AssigneeLookupResult) taskResults.get(ASSIGNEE_LOOKUP_TASK);

            mcpSyncRequestContext.progress(p -> p.percentage(80).message("Compiling workflow results"));

            JiraIssueCreatePayload payload = new JiraIssueCreatePayload(
                    userStoryResult.getSummary(),
                    userStoryResult.getDetailedStory(),
                    userStoryResult.getAcceptanceCriteria(),
                    assigneeLookupResult.assigneeId()
            );

            mcpSyncRequestContext.progress(p -> p.percentage(100).message("Create Jira workflow completed"));

            return payload;

        } catch (Exception exception) {
            log.error("Create Jira workflow failed", exception);
            throw new JiraException("Create Jira workflow failed: " + exception.getMessage(), exception);
        }
    }
}
