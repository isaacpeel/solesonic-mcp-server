package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.model.atlassian.agile.Board;
import com.solesonic.mcp.model.atlassian.agile.BoardIssue;
import com.solesonic.mcp.model.atlassian.agile.BoardIssues;
import com.solesonic.mcp.model.atlassian.agile.Boards;
import com.solesonic.mcp.model.atlassian.jira.*;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import com.solesonic.mcp.workflow.agile.AgileQueryResult;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowContext;
import com.solesonic.mcp.tool.McpConfirmations;
import com.solesonic.mcp.workflow.framework.WorkflowEvent;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.mcp.service.atlassian.AtlassianConstants.*;
import static com.solesonic.mcp.workflow.agile.AgileChatClientConfig.AGILE_CHAT_CLIENT;

@Service
public class JiraAgileService {
    private static final Logger log = LoggerFactory.getLogger(JiraAgileService.class);

    private static final String CHAT_ID = "chatId";

    private static final int ISSUE_FETCH_CONCURRENCY = 5;
    private static final int TRANSITION_CONCURRENCY = 5;
    private static final int TRANSITION_FETCH_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 15;

    private static final String ISSUE_ENRICHMENT_PROMPT_TEMPLATE = """
            You are a helpful Jira assistant. Format a clear, concise response to the user's question using the Jira issue data provided.

            User question: %s

            Board: %s (showing %d of %d issues)

            Issues:
            %s

            Instructions:
            - Format the response in clean markdown
            - Use issue keys as clickable markdown links using the link provided for each issue
            - Always include: issue key (as link), summary, status, and assignee for each issue
            - Include description content only when it adds value to answering the question
            - If an issue has no assignee, say "Unassigned"
            - Keep descriptions concise — two or three sentences at most
            - Do not include a preamble or closing remarks, just the formatted issue list
            """;

    private record PagedQueryResult(List<BoardIssue> issues, int total, boolean hasMorePages, int nextStartAt) {}

    public static final String START_AT = "startAt";
    public static final String MAX_RESULTS = "maxResults";
    public static final String TYPE = "type";
    public static final String NAME = "name";
    public static final String PROJECT_KEY_OR_ID = "projectKeyOrId";
    public static final String JQL = "jql";
    public static final String VALIDATE_QUERY = "validateQuery";

    @Value("${solesonic.llm.jira.cloud.id.path}")
    private String cloudIdPath;

    @Value("${jira.url.template}")
    private String jiraUrlTemplate;

    private final WebClient webClient;
    private final JiraIssueService jiraIssueService;
    private final ChatClient chatClient;

    public JiraAgileService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient,
                            JiraIssueService jiraIssueService,
                            @Qualifier(AGILE_CHAT_CLIENT) ChatClient chatClient) {
        this.webClient = webClient;
        this.jiraIssueService = jiraIssueService;
        this.chatClient = chatClient;
    }

    public Boards listBoards(JiraAgileTools.ListBoardsRequest listBoardsRequest) {
        log.info("Listing Jira boards");

        String[] baseUri = {EX, JIRA, cloudIdPath, REST_PATH, AGILE_PATH, AGILE_VERSION_PATH, BOARD_PATH};

        Boards boards = webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.pathSegment(baseUri);
                    uriBuilder.queryParamIfPresent(START_AT, Optional.ofNullable(listBoardsRequest.startAt()));
                    uriBuilder.queryParamIfPresent(MAX_RESULTS, Optional.ofNullable(listBoardsRequest.maxResults()));
                    uriBuilder.queryParamIfPresent(TYPE, Optional.ofNullable(listBoardsRequest.type()));
                    uriBuilder.queryParamIfPresent(NAME, Optional.ofNullable(listBoardsRequest.name()));
                    uriBuilder.queryParamIfPresent(PROJECT_KEY_OR_ID, Optional.ofNullable(listBoardsRequest.projectKeyOrId()));

                    return uriBuilder.build();
                })
                .exchangeToMono(response -> response.bodyToMono(Boards.class))
                .block();

        log.info("Jira boards retrieved successfully");
        return boards;
    }

    public Board getBoard(String boardId) {
        log.debug("Getting Jira board: {}", boardId);

        String[] base = {EX, JIRA, cloudIdPath, REST_PATH, AGILE_PATH, AGILE_VERSION_PATH, BOARD_PATH, boardId};

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(base)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(Board.class))
                .block();
    }

    @SuppressWarnings("unused")
    public String getBoardConfiguration(String boardId) {
        log.debug("Getting Jira board configuration: {}", boardId);

        String[] base = {EX, JIRA, cloudIdPath, REST_PATH, AGILE_PATH, AGILE_VERSION_PATH, BOARD_PATH, boardId, CONFIGURATION_PATH};

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(base)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(String.class))
                .block();
    }

    public BoardIssues getBoardIssues(JiraAgileTools.BoardIssuesRequest boardIssuesRequest) {
        String boardId = boardIssuesRequest.boardId();
        log.info("Getting Jira board issues for board ID: {}", boardId);

        String[] base = {EX, JIRA, cloudIdPath, REST_PATH, AGILE_PATH, AGILE_VERSION_PATH, BOARD_PATH, boardId, ISSUE_PATH};

        BoardIssues boardIssues = webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.pathSegment(base);

                    String jql = boardIssuesRequest.jql();
                    Integer maxResults = boardIssuesRequest.maxResults();
                    Integer startAt = boardIssuesRequest.startAt();

                    if (StringUtils.isNotEmpty(jql)) {
                        uriBuilder.queryParam(JQL, jql);
                    }

                    uriBuilder.queryParam(MAX_RESULTS, Objects.requireNonNullElse(maxResults, 15));

                    if (startAt != null) {
                        uriBuilder.queryParam(START_AT, startAt);
                    }

                    uriBuilder.queryParam(VALIDATE_QUERY, boardIssuesRequest.validateQuery());

                    return uriBuilder.build();
                })
                .exchangeToMono(response -> response.bodyToMono(BoardIssues.class))
                .block();

        assert boardIssues != null;
        List<BoardIssue> issues = boardIssues.issues();
        log.info("Found {} issues",  issues.size());

        return boardIssues;
    }

    public String handleBoardSelection(
            McpSyncRequestContext mcpSyncRequestContext,
            AgileQueryWorkflowContext workflowContext
    ) {
        List<Board> boards = workflowContext.getBoards();

        if (boards.isEmpty()) {
            return "No accessible Jira boards were found.";
        }

        if (boards.size() == 1) {
            Board selectedBoard = boards.getFirst();
            log.info("Single board found, auto-selecting: {} (ID: {})", selectedBoard.name(), selectedBoard.id());
            emitProgress(workflowContext, "Using board: %s".formatted(selectedBoard.name()));
            return dispatchBoardAction(mcpSyncRequestContext, selectedBoard, workflowContext);
        } else {
            throw new IllegalStateException("Board should be selected by agent workflow.");
        }
    }

    private void emitProgress(AgileQueryWorkflowContext workflowContext, String message) {
        WorkflowExecutionContext executionContext = workflowContext.getExecutionContext();
        if (executionContext == null) return;
        executionContext.notificationService().publish(
                WorkflowEvent.stepProgress(
                        executionContext.workflowName(),
                        "dispatch",
                        executionContext.correlationId(),
                        100,
                        message
                )
        );
    }

    private String dispatchBoardAction(
            McpSyncRequestContext mcpSyncRequestContext,
            Board board,
            AgileQueryWorkflowContext workflowContext
    ) {
        AgileQueryResult agileQueryResult = workflowContext.getAgileQueryResult();
        String userMessage = workflowContext.getOriginalUserMessage();

        if (agileQueryResult.isTransitionQuery()) {
            return executeBulkTransition(mcpSyncRequestContext, board, workflowContext);
        }

        return executePagedBoardQuery(mcpSyncRequestContext, board, agileQueryResult, userMessage, agileQueryResult.resolvedStartAt(), workflowContext);
    }

    private String executeBulkTransition(
            McpSyncRequestContext mcpSyncRequestContext,
            Board board,
            AgileQueryWorkflowContext workflowContext
    ) {
        AgileQueryResult agileQueryResult = workflowContext.getAgileQueryResult();
        String targetStatus = agileQueryResult.targetStatus();
        String jqlFilter = agileQueryResult.jqlFilter() == null ? "" : agileQueryResult.jqlFilter().strip();
        boolean batchingRequired = workflowContext.isRequiresBatching();
        int estimatedCount = workflowContext.getEstimatedItemCount();

        log.info("Bulk transition requested on board '{}' to status '{}' with JQL: '{}' (batching={}, estimated={})",
                board.name(), targetStatus, jqlFilter, batchingRequired, estimatedCount);

        if (batchingRequired) {
            return executeBatchedTransition(mcpSyncRequestContext, board, workflowContext);
        }

        emitProgress(workflowContext, "Collecting issues to transition on board '%s'…".formatted(board.name()));
        List<String> issueKeys = collectAllMatchingIssueKeys(board, jqlFilter, 0);

        if (issueKeys.isEmpty()) {
            return "No issues found matching the filter on board **%s**.".formatted(board.name());
        }

        String transitionId = resolveTransitionId(issueKeys.getFirst(), targetStatus);

        String confirmationMessage = "This will transition **%d** issue(s) on board **%s** to **%s**. Proceed?"
                .formatted(issueKeys.size(), board.name(), targetStatus);

        McpSchema.ElicitResult elicitResult = McpConfirmations.confirm(
                mcpSyncRequestContext,
                confirmationMessage,
                resolveElicitationMetadata(mcpSyncRequestContext)
        );

        return switch (elicitResult.action()) {
            case ACCEPT -> {
                emitProgress(workflowContext, "Transitioning %d issue(s) to '%s'…".formatted(issueKeys.size(), targetStatus));
                yield applyTransitions(issueKeys, transitionId, targetStatus, board);
            }
            case DECLINE, CANCEL -> "Transition cancelled.";
        };
    }

    private String executeBatchedTransition(
            McpSyncRequestContext mcpSyncRequestContext,
            Board board,
            AgileQueryWorkflowContext workflowContext
    ) {
        AgileQueryResult agileQueryResult = workflowContext.getAgileQueryResult();
        String targetStatus = agileQueryResult.targetStatus();
        String jqlFilter = agileQueryResult.jqlFilter() == null ? "" : agileQueryResult.jqlFilter().strip();
        int batchSize = workflowContext.getBatchSize();
        int estimatedCount = workflowContext.getEstimatedItemCount();
        int totalBatches = (int) Math.ceil((double) estimatedCount / batchSize);

        String confirmationMessage = "This will transition approximately **%d** issue(s) on board **%s** to **%s** in **%d** batches of %d. Proceed?"
                .formatted(estimatedCount, board.name(), targetStatus, totalBatches, batchSize);

        McpSchema.ElicitResult elicitResult = McpConfirmations.confirm(
                mcpSyncRequestContext,
                confirmationMessage,
                resolveElicitationMetadata(mcpSyncRequestContext)
        );

        return switch (elicitResult.action()) {
            case ACCEPT -> executeTransitionBatches(board, jqlFilter, targetStatus, batchSize, estimatedCount, workflowContext);
            case DECLINE, CANCEL -> "Transition cancelled.";
        };
    }

    private String executeTransitionBatches(
            Board board,
            String jqlFilter,
            String targetStatus,
            int batchSize,
            int estimatedCount,
            AgileQueryWorkflowContext workflowContext
    ) {
        int totalBatches = (int) Math.ceil((double) estimatedCount / batchSize);
        log.info("Starting batched transition: {} estimated items in {} batches of {}", estimatedCount, totalBatches, batchSize);

        long accumulatedSuccessCount = 0;
        long accumulatedFailureCount = 0;
        int startAt = 0;

        while (true) {
            int currentBatchNumber = (startAt / batchSize) + 1;
            log.info("Processing transition batch {}/{} (startAt={})", currentBatchNumber, totalBatches, startAt);
            emitProgress(workflowContext, "Transitioning batch %d of %d to '%s'…".formatted(currentBatchNumber, totalBatches, targetStatus));

            JiraAgileTools.BoardIssuesRequest batchRequest = new JiraAgileTools.BoardIssuesRequest(
                    String.valueOf(board.id()),
                    jqlFilter.isEmpty() ? null : jqlFilter,
                    startAt == 0 ? null : startAt,
                    batchSize,
                    false
            );

            BoardIssues boardIssues = getBoardIssues(batchRequest);
            List<String> issueKeys = boardIssues.issues().stream()
                    .map(BoardIssue::key)
                    .toList();

            if (issueKeys.isEmpty()) {
                break;
            }

            String transitionId = resolveTransitionId(issueKeys.getFirst(), targetStatus);
            BatchTransitionResult batchResult = applyTransitionsAndCount(issueKeys, transitionId);

            accumulatedSuccessCount += batchResult.successCount();
            accumulatedFailureCount += batchResult.failureCount();

            int total = boardIssues.total() != null ? boardIssues.total() : 0;
            startAt += issueKeys.size();

            if (startAt >= total || issueKeys.size() < batchSize) {
                break;
            }
        }

        return buildBatchTransitionSummary(board, targetStatus, accumulatedSuccessCount, accumulatedFailureCount);
    }

    private record BatchTransitionResult(long successCount, long failureCount) {}

    private BatchTransitionResult applyTransitionsAndCount(List<String> issueKeys, String transitionId) {
        int concurrency = Math.min(TRANSITION_CONCURRENCY, issueKeys.size());
        ExecutorService transitionExecutor = Executors.newFixedThreadPool(concurrency);

        try {
            List<CompletableFuture<Boolean>> futures = issueKeys.stream()
                    .map(issueKey -> CompletableFuture.supplyAsync(() -> {
                        try {
                            jiraIssueService.transitionIssue(issueKey, transitionId);
                            return true;
                        } catch (Exception exception) {
                            log.warn("Failed to transition issue {}: {}", issueKey, exception.getMessage());
                            return false;
                        }
                    }, transitionExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long successCount = futures.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
            long failureCount = issueKeys.size() - successCount;
            return new BatchTransitionResult(successCount, failureCount);
        } finally {
            transitionExecutor.shutdown();
        }
    }

    private String buildBatchTransitionSummary(
            Board board,
            String targetStatus,
            long totalSuccessCount,
            long totalFailureCount
    ) {
        long totalProcessed = totalSuccessCount + totalFailureCount;
        StringBuilder summary = new StringBuilder();
        summary.append("**%s** — Transitioned **%d** of **%d** issues to **%s**."
                .formatted(board.name(), totalSuccessCount, totalProcessed, targetStatus));

        if (totalFailureCount > 0) {
            summary.append("\n\n**%d** issue(s) failed to transition.".formatted(totalFailureCount));
        }

        return summary.toString();
    }

    private List<String> collectAllMatchingIssueKeys(Board board, String jqlFilter, int startAt) {
        List<String> allKeys = new ArrayList<>();
        int currentStartAt = startAt;

        while (true) {
            JiraAgileTools.BoardIssuesRequest boardIssuesRequest = new JiraAgileTools.BoardIssuesRequest(
                    String.valueOf(board.id()),
                    jqlFilter.isEmpty() ? null : jqlFilter,
                    currentStartAt == 0 ? null : currentStartAt,
                    TRANSITION_FETCH_PAGE_SIZE,
                    false
            );

            BoardIssues boardIssues = getBoardIssues(boardIssuesRequest);
            List<String> pageKeys = boardIssues.issues().stream()
                    .map(BoardIssue::key)
                    .toList();

            allKeys.addAll(pageKeys);

            int total = boardIssues.total() != null ? boardIssues.total() : pageKeys.size();
            currentStartAt += pageKeys.size();

            if (currentStartAt >= total || pageKeys.isEmpty()) {
                break;
            }
        }

        return allKeys;
    }

    private String resolveTransitionId(String sampleIssueKey, String targetStatus) {
        Transitions transitions = jiraIssueService.getTransitions(sampleIssueKey);

        return transitions.transitions().stream()
                .filter(transition -> transition.name().equalsIgnoreCase(targetStatus)
                        || (transition.to() != null && transition.to().name().equalsIgnoreCase(targetStatus)))
                .findFirst()
                .map(Transition::id)
                .orElseThrow(() -> {
                    String availableTransitions = transitions.transitions().stream()
                            .map(Transition::name)
                            .reduce((first, second) -> first + ", " + second)
                            .orElse("none");
                    return new IllegalStateException(
                            "No transition to status '%s' is available. Available transitions: %s"
                                    .formatted(targetStatus, availableTransitions));
                });
    }

    private String applyTransitions(List<String> issueKeys, String transitionId, String targetStatus, Board board) {
        log.info("Applying transition '{}' to {} issues on board '{}'", transitionId, issueKeys.size(), board.name());

        int concurrency = Math.min(TRANSITION_CONCURRENCY, issueKeys.size());
        ExecutorService transitionExecutor = Executors.newFixedThreadPool(concurrency);

        try {
            List<CompletableFuture<String>> futures = issueKeys.stream()
                    .map(issueKey -> CompletableFuture.supplyAsync(() -> {
                        try {
                            jiraIssueService.transitionIssue(issueKey, transitionId);
                            return issueKey + ": transitioned";
                        } catch (Exception exception) {
                            log.warn("Failed to transition issue {}: {}", issueKey, exception.getMessage());
                            return issueKey + ": failed — " + exception.getMessage();
                        }
                    }, transitionExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<String> results = futures.stream().map(CompletableFuture::join).toList();
            long successCount = results.stream().filter(result -> result.endsWith("transitioned")).count();
            long failureCount = results.size() - successCount;

            StringBuilder summary = new StringBuilder();
            summary.append("**%s** — Transitioned **%d** of **%d** issues to **%s**."
                    .formatted(board.name(), successCount, results.size(), targetStatus));

            if (failureCount > 0) {
                summary.append("\n\nFailed issues:\n");
                results.stream()
                        .filter(result -> !result.endsWith("transitioned"))
                        .forEach(result -> summary.append("- ").append(result).append("\n"));
            }

            return summary.toString();
        } finally {
            transitionExecutor.shutdown();
        }
    }

    private String executePagedBoardQuery(
            McpSyncRequestContext mcpSyncRequestContext,
            Board board,
            AgileQueryResult agileQueryResult,
            String userMessage,
            int startAt,
            AgileQueryWorkflowContext workflowContext
    ) {
        if (agileQueryResult.isCountQuery()) {
            emitProgress(workflowContext, "Counting issues on board '%s'…".formatted(board.name()));
            PagedQueryResult firstPage = fetchPage(board, agileQueryResult, startAt);
            return formatCountResult(board, firstPage, agileQueryResult);
        }
        emitProgress(workflowContext, "Fetching issues from board '%s'…".formatted(board.name()));
        return collectAndFormatPages(mcpSyncRequestContext, board, agileQueryResult, userMessage, startAt, new ArrayList<>(), workflowContext);
    }

    private String collectAndFormatPages(
            McpSyncRequestContext mcpSyncRequestContext,
            Board board,
            AgileQueryResult agileQueryResult,
            String userMessage,
            int startAt,
            List<BoardIssue> accumulatedIssues,
            AgileQueryWorkflowContext workflowContext
    ) {
        PagedQueryResult pagedResult = fetchPage(board, agileQueryResult, startAt);
        accumulatedIssues.addAll(pagedResult.issues());

        if (!pagedResult.hasMorePages()) {
            return enrichAndFormatIssueList(board, accumulatedIssues, userMessage, accumulatedIssues.size(), pagedResult.total(), workflowContext);
        }

        String elicitMessage = "Loaded issues %d–%d of %d. Would you like to load the next page?"
                .formatted(startAt + 1, pagedResult.nextStartAt(), pagedResult.total());

        try {
            McpSchema.ElicitResult elicitResult = McpConfirmations.confirm(
                    mcpSyncRequestContext,
                    elicitMessage,
                    resolveElicitationMetadata(mcpSyncRequestContext)
            );

            return switch (elicitResult.action()) {
                case ACCEPT -> collectAndFormatPages(
                        mcpSyncRequestContext, board, agileQueryResult, userMessage, pagedResult.nextStartAt(), accumulatedIssues, workflowContext
                );
                case DECLINE, CANCEL -> enrichAndFormatIssueList(board, accumulatedIssues, userMessage, accumulatedIssues.size(), pagedResult.total(), workflowContext);
            };
        } catch (Exception exception) {
            log.warn("Load more elicitation unavailable, returning current accumulated results: {}", exception.getMessage());
            return enrichAndFormatIssueList(board, accumulatedIssues, userMessage, accumulatedIssues.size(), pagedResult.total(), workflowContext);
        }
    }

    private PagedQueryResult fetchPage(Board board, AgileQueryResult agileQueryResult, int startAt) {
        String jqlFilter = agileQueryResult.jqlFilter() == null ? "" : agileQueryResult.jqlFilter().strip();
        log.info("Fetching page for board '{}' (ID: {}) startAt={} with JQL: '{}'", board.name(), board.id(), startAt, jqlFilter);

        JiraAgileTools.BoardIssuesRequest boardIssuesRequest = new JiraAgileTools.BoardIssuesRequest(
                String.valueOf(board.id()),
                jqlFilter.isEmpty() ? null : jqlFilter,
                startAt == 0 ? null : startAt,
                DEFAULT_PAGE_SIZE,
                false
        );

        BoardIssues boardIssues = getBoardIssues(boardIssuesRequest);

        int total = boardIssues.total() != null ? boardIssues.total() : boardIssues.issues().size();
        int fetchedCount = boardIssues.issues().size();
        int nextStartAt = startAt + fetchedCount;
        boolean hasMorePages = nextStartAt < total;

        return new PagedQueryResult(boardIssues.issues(), total, hasMorePages, nextStartAt);
    }

    private String enrichAndFormatIssueList(
            Board board,
            List<BoardIssue> boardIssues,
            String userMessage,
            int shownCount,
            int total,
            AgileQueryWorkflowContext workflowContext
    ) {
        if (boardIssues.isEmpty()) {
            return "**%s** — No issues found.".formatted(board.name());
        }

        List<String> issueKeys = boardIssues.stream()
                .map(BoardIssue::key)
                .toList();

        log.info("Fetching full details for {} issue(s) on board '{}'", issueKeys.size(), board.name());
        emitProgress(workflowContext, "Loading details for %d issue(s)…".formatted(issueKeys.size()));

        int concurrency = Math.min(ISSUE_FETCH_CONCURRENCY, issueKeys.size());
        ExecutorService fetchExecutor = Executors.newFixedThreadPool(concurrency);

        try {
            List<CompletableFuture<JiraIssue>> futures = issueKeys.stream()
                    .map(issueKey -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return jiraIssueService.get(issueKey);
                        } catch (Exception exception) {
                            log.warn("Failed to fetch details for issue {}: {}", issueKey, exception.getMessage());
                            return null;
                        }
                    }, fetchExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<JiraIssue> fullIssues = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();

            String issueData = buildIssueDataForLlm(fullIssues);
            String prompt = ISSUE_ENRICHMENT_PROMPT_TEMPLATE.formatted(
                    userMessage, board.name(), shownCount, total, issueData
            );

            log.info("Requesting LLM enrichment for {} issue(s)", fullIssues.size());
            emitProgress(workflowContext, "Formatting results…");
            return chatClient.prompt().user(prompt).call().content();
        } finally {
            fetchExecutor.shutdown();
        }
    }

    private String buildIssueDataForLlm(List<JiraIssue> issues) {
        StringBuilder issueDataBuilder = new StringBuilder();
        for (JiraIssue issue : issues) {
            String issueLink = jiraUrlTemplate.replace("{key}", issue.key());
            issueDataBuilder.append("Key: ").append(issue.key()).append("\n");
            issueDataBuilder.append("Link: ").append(issueLink).append("\n");

            Fields fields = issue.fields();
            if (fields != null) {
                if (fields.summary() != null) {
                    issueDataBuilder.append("Summary: ").append(fields.summary()).append("\n");
                }
                if (fields.status() != null && fields.status().name() != null) {
                    issueDataBuilder.append("Status: ").append(fields.status().name()).append("\n");
                }
                if (fields.assignee() != null && fields.assignee().displayName() != null) {
                    issueDataBuilder.append("Assignee: ").append(fields.assignee().displayName()).append("\n");
                } else {
                    issueDataBuilder.append("Assignee: Unassigned\n");
                }
                String descriptionText = extractDescriptionText(fields.description());
                if (!descriptionText.isBlank()) {
                    issueDataBuilder.append("Description: ").append(descriptionText).append("\n");
                }
            }
            issueDataBuilder.append("\n");
        }
        return issueDataBuilder.toString();
    }

    private String extractDescriptionText(Description description) {
        if (description == null || description.content() == null) {
            return "";
        }
        StringBuilder textBuilder = new StringBuilder();
        for (Content contentBlock : description.content()) {
            appendContentBlockText(contentBlock, textBuilder);
        }
        return textBuilder.toString().strip();
    }

    private void appendContentBlockText(Content contentBlock, StringBuilder textBuilder) {
        if (contentBlock.content() == null) {
            return;
        }
        for (TextContent textContent : contentBlock.content()) {
            appendTextContentText(textContent, textBuilder);
        }
    }

    private void appendTextContentText(TextContent textContent, StringBuilder textBuilder) {
        if (textContent.text() != null) {
            if (!textBuilder.isEmpty()) {
                textBuilder.append(" ");
            }
            textBuilder.append(textContent.text());
        }
        if (textContent.content() != null) {
            for (TextContent nestedContent : textContent.content()) {
                appendTextContentText(nestedContent, textBuilder);
            }
        }
    }

    private String formatCountResult(Board board, PagedQueryResult pagedResult, AgileQueryResult agileQueryResult) {
        int total = pagedResult.total();
        boolean noFilter = agileQueryResult.jqlFilter() == null || agileQueryResult.jqlFilter().isBlank();
        String jqlDescription = noFilter
                ? "all issues"
                : "issues matching `" + agileQueryResult.jqlFilter() + "`";
        return "**%s** — %d %s (%s)".formatted(board.name(), total, total == 1 ? "issue" : "issues", jqlDescription);
    }

    @SuppressWarnings("unused")
    public String buildBoardSelectionMessage(List<Board> boards) {
        StringBuilder message = new StringBuilder();
        message.append("Multiple Jira boards are available. Please enter the ID of the board you'd like to query:\n\n");

        boards.forEach(board ->
                message.append("- **").append(board.name()).append("** — ID: `").append(board.id())
                        .append("`, Type: ").append(board.type()).append("\n")
        );

        return message.toString();
    }

    private Map<String, Object> resolveElicitationMetadata(McpSyncRequestContext mcpSyncRequestContext) {
        Map<String, Object> requestMetadata = mcpSyncRequestContext.requestMeta();

        if (requestMetadata != null && requestMetadata.containsKey(CHAT_ID)) {
            return Map.of(CHAT_ID, requestMetadata.get(CHAT_ID).toString());
        }

        return Map.of(CHAT_ID, UUID.randomUUID().toString());
    }
}
