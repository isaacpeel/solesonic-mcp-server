package com.solesonic.mcp.workflow;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.workflow.chain.UserStoryChainContext;
import com.solesonic.mcp.workflow.chain.UserStoryPromptChain;
import com.solesonic.mcp.workflow.model.AssigneeLookupResult;
import com.solesonic.mcp.workflow.model.JiraIssueCreatePayload;
import com.solesonic.mcp.workflow.service.AssigneeResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class CreateJiraWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CreateJiraWorkflow.class);

    private static final String USER_STORY_TASK = "user-story-generation";
    private static final String ASSIGNEE_LOOKUP_TASK = "assignee-lookup";
    private static final String FINALIZE_TASK = "finalize";

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

    public Mono<JiraIssueCreatePayload> startWorkflow(McpAsyncRequestContext mcpAsyncRequestContext, String userMessage) {
        try {
            ProgressReporter progressReporter = new ProgressReporter(mcpAsyncRequestContext);

            WeightedProgressCoordinator progress = new WeightedProgressCoordinator(progressReporter, Map.of(
                    USER_STORY_TASK, 0.70,
                    ASSIGNEE_LOOKUP_TASK, 0.20,
                    FINALIZE_TASK, 0.10
            ));

            WeightedProgressCoordinator.TaskProgress userStoryProgress = progress.task(USER_STORY_TASK);
            WeightedProgressCoordinator.TaskProgress assigneeProgress = progress.task(ASSIGNEE_LOOKUP_TASK);
            WeightedProgressCoordinator.TaskProgress finalizeProgress = progress.task(FINALIZE_TASK);

            progress.startup("Generating user story");
            progress.startup("Preparing assignee lookup");
            progress.startup("Workflow started");

            return parallelOrchestrator.executeParallel(List.of(
                    new ParallelOrchestrator.AgentTask<>(
                            USER_STORY_TASK,
                            () -> userStoryPromptChain.run(userMessage, userStoryProgress)
                    ),
                    new ParallelOrchestrator.AgentTask<>(
                            ASSIGNEE_LOOKUP_TASK,
                            () -> assigneeResolutionService.resolve(userMessage, assigneeProgress)
                    )
            )).map(taskResults -> {
                UserStoryChainContext userStoryResult = (UserStoryChainContext) taskResults.get(USER_STORY_TASK);
                AssigneeLookupResult assigneeLookupResult = (AssigneeLookupResult) taskResults.get(ASSIGNEE_LOOKUP_TASK);

                finalizeProgress.update(0.5, "Compiling workflow results");

                JiraIssueCreatePayload payload = new JiraIssueCreatePayload(
                        userStoryResult.getSummary(),
                        userStoryResult.getDetailedStory(),
                        userStoryResult.getAcceptanceCriteria(),
                        assigneeLookupResult.assigneeId()
                );

                finalizeProgress.done("Create Jira workflow completed");

                return payload;
            });
        } catch (Exception exception) {
            log.error("Create Jira workflow failed", exception);
            return Mono.error(new JiraException("Create Jira workflow failed: " + exception.getMessage(), exception));
        }
    }
}