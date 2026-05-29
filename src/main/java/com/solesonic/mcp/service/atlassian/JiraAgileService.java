package com.solesonic.mcp.service.atlassian;

import com.solesonic.agent.agile.AgileQueryResult;
import com.solesonic.agent.agile.AgileState;
import com.solesonic.mcp.model.atlassian.agile.Board;
import com.solesonic.mcp.model.atlassian.agile.BoardIssue;
import com.solesonic.mcp.model.atlassian.agile.BoardIssues;
import com.solesonic.mcp.model.atlassian.agile.Boards;
import com.solesonic.mcp.model.atlassian.jira.Content;
import com.solesonic.mcp.model.atlassian.jira.Description;
import com.solesonic.mcp.model.atlassian.jira.Fields;
import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import com.solesonic.mcp.model.atlassian.jira.TextContent;
import com.solesonic.mcp.model.atlassian.jira.Transition;
import com.solesonic.mcp.model.atlassian.jira.Transitions;
import com.solesonic.mcp.tool.McpConfirmations;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.solesonic.agent.agile.AgileChatClientConfig.AGILE_CHAT_CLIENT;
import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.mcp.service.atlassian.AtlassianConstants.*;

@Service
public class JiraAgileService {
    private static final Logger log = LoggerFactory.getLogger(JiraAgileService.class);

    public static final String START_AT = "startAt";
    public static final String MAX_RESULTS = "maxResults";
    public static final String TYPE = "type";
    public static final String NAME = "name";
    public static final String PROJECT_KEY_OR_ID = "projectKeyOrId";
    public static final String JQL = "jql";
    public static final String VALIDATE_QUERY = "validateQuery";

    private static final String CHAT_ID = "chatId";
    private static final int ISSUE_FETCH_CONCURRENCY = 5;
    private static final int TRANSITION_CONCURRENCY = 5;
    private static final int TRANSITION_FETCH_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 15;
    private static final int DEFAULT_BATCH_SIZE = 20;

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

    @Value("${solesonic.llm.jira.cloud.id.path}")
    private String cloudIdPath;

    @Value("${jira.url.template}")
    private String jiraUrlTemplate;

    private final WebClient webClient;
    private final JiraIssueService jiraIssueService;
    private final ChatClient chatClient;

    public JiraAgileService(
            @Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient,
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
        log.info("Found {} issues", issues.size());

        return boardIssues;
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

    public String handleCountQuery(Board board, AgileQueryResult queryResult) {
        JiraAgileTools.BoardIssuesRequest request = new JiraAgileTools.BoardIssuesRequest(
                String.valueOf(board.id()),
                emptyToNull(queryResult.jqlFilter()),
                null, 0, false
        );
        BoardIssues boardIssues = getBoardIssues(request);
        int total = boardIssues.total() != null ? boardIssues.total() : 0;
        boolean noFilter = queryResult.jqlFilter() == null || queryResult.jqlFilter().isBlank();
        String jqlDescription = noFilter
                ? "all issues"
                : "issues matching `" + queryResult.jqlFilter() + "`";
        return "**%s** — %d %s (%s)".formatted(board.name(), total, total == 1 ? "issue" : "issues", jqlDescription);
    }

    public String handleListQuery(
            McpSyncRequestContext mcpSyncRequestContext,
            Board board,
            AgileQueryResult queryResult,
            String userMessage
    ) {
        return collectAndFormatPages(
                mcpSyncRequestContext, board, queryResult, userMessage,
                queryResult.resolvedStartAt(), new ArrayList<>(), true
        );
    }

    private String collectAndFormatPages(
            McpSyncRequestContext mcpSyncRequestContext,
            Board board,
            AgileQueryResult queryResult,
            String userMessage,
            int startAt,
            List<BoardIssue> accumulatedIssues,
            boolean shouldElicit
    ) {
        PagedQueryResult pagedResult = fetchPage(board, queryResult, startAt);
        accumulatedIssues.addAll(pagedResult.issues());

        if (!pagedResult.hasMorePages()) {
            return enrichAndFormatIssueList(board, accumulatedIssues, userMessage,
                    accumulatedIssues.size(), pagedResult.total());
        }

        if (!shouldElicit) {
            return collectAndFormatPages(
                    mcpSyncRequestContext, board, queryResult, userMessage,
                    pagedResult.nextStartAt(), accumulatedIssues, false
            );
        }

        String elicitMessage = "Loaded issues %d–%d of %d. Would you like to load the next page?"
                .formatted(startAt + 1, pagedResult.nextStartAt(), pagedResult.total());

        try {
            ElicitResult elicitResult = McpConfirmations.confirm(
                    mcpSyncRequestContext, elicitMessage,
                    resolveElicitationMetadata(mcpSyncRequestContext)
            );

            return switch (elicitResult.action()) {
                case ACCEPT -> collectAndFormatPages(
                        mcpSyncRequestContext, board, queryResult, userMessage,
                        pagedResult.nextStartAt(), accumulatedIssues, false
                );
                case DECLINE, CANCEL -> enrichAndFormatIssueList(board, accumulatedIssues, userMessage,
                        accumulatedIssues.size(), pagedResult.total());
            };
        } catch (Exception exception) {
            log.warn("Pagination elicitation unavailable, returning accumulated results: {}", exception.getMessage());
            return enrichAndFormatIssueList(board, accumulatedIssues, userMessage,
                    accumulatedIssues.size(), pagedResult.total());
        }
    }

    private PagedQueryResult fetchPage(Board board, AgileQueryResult queryResult, int startAt) {
        JiraAgileTools.BoardIssuesRequest request = new JiraAgileTools.BoardIssuesRequest(
                String.valueOf(board.id()),
                emptyToNull(queryResult.jqlFilter()),
                startAt == 0 ? null : startAt,
                DEFAULT_PAGE_SIZE,
                false
        );
        BoardIssues boardIssues = getBoardIssues(request);
        int total = boardIssues.total() != null ? boardIssues.total() : boardIssues.issues().size();
        int fetchedCount = boardIssues.issues().size();
        int nextStartAt = startAt + fetchedCount;
        return new PagedQueryResult(boardIssues.issues(), total, nextStartAt < total, nextStartAt);
    }

    private String enrichAndFormatIssueList(
            Board board,
            List<BoardIssue> boardIssues,
            String userMessage,
            int shownCount,
            int total
    ) {
        if (boardIssues.isEmpty()) {
            return "**%s** — No issues found.".formatted(board.name());
        }

        List<String> issueKeys = boardIssues.stream().map(BoardIssue::key).toList();

        ExecutorService fetchExecutor = Executors.newFixedThreadPool(
                Math.min(ISSUE_FETCH_CONCURRENCY, issueKeys.size()));

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

            List<JiraIssue> fullIssues = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();

            String issueData = buildIssueDataForLlm(fullIssues);
            String prompt = ISSUE_ENRICHMENT_PROMPT_TEMPLATE.formatted(
                    userMessage, board.name(), shownCount, total, issueData);

            return chatClient.prompt().user(prompt).call().content();
        } finally {
            fetchExecutor.shutdown();
        }
    }

    private String buildIssueDataForLlm(List<JiraIssue> issues) {
        StringBuilder builder = new StringBuilder();
        for (JiraIssue issue : issues) {
            String issueLink = jiraUrlTemplate.replace("{key}", issue.key());
            builder.append("Key: ").append(issue.key()).append("\n");
            builder.append("Link: ").append(issueLink).append("\n");

            Fields fields = issue.fields();
            if (fields != null) {
                if (fields.summary() != null) {
                    builder.append("Summary: ").append(fields.summary()).append("\n");
                }

                if (fields.status() != null && fields.status().name() != null) {
                    builder.append("Status: ").append(fields.status().name()).append("\n");
                }

                if (fields.assignee() != null && fields.assignee().displayName() != null) {
                    builder.append("Assignee: ").append(fields.assignee().displayName()).append("\n");
                } else {
                    builder.append("Assignee: Unassigned\n");
                }

                String descriptionText = extractDescriptionText(fields.description());
                if (!descriptionText.isBlank()) {
                    builder.append("Description: ").append(descriptionText).append("\n");
                }
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String extractDescriptionText(Description description) {
        if (description == null || description.content() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Content contentBlock : description.content()) {
            appendContentBlockText(builder, contentBlock);
        }
        return builder.toString().strip();
    }

    private void appendContentBlockText(StringBuilder builder, Content content) {
        if (content == null || content.content() == null) {
            return;
        }
        for (TextContent textContent : content.content()) {
            appendTextContentText(builder, textContent);
        }
    }

    private void appendTextContentText(StringBuilder builder, TextContent textContent) {
        if (textContent == null) {
            return;
        }
        if (textContent.text() != null) {
            builder.append(textContent.text());
        }
        if (textContent.content() != null) {
            for (TextContent child : textContent.content()) {
                appendTextContentText(builder, child);
            }
        }
    }

    public String handleTransitionQuery(
            McpSyncRequestContext mcpSyncRequestContext,
            Board board,
            AgileQueryResult queryResult,
            AgileState state
    ) {
        String targetStatus = queryResult.targetStatus();
        String jqlFilter = emptyToNull(queryResult.jqlFilter());
        boolean requiresBatching = state.requiresBatching().orElse(false);

        log.info("Transition requested on board '{}' to '{}', batching={}", board.name(), targetStatus, requiresBatching);

        if (requiresBatching) {
            return executeBatchedTransition(mcpSyncRequestContext, board, queryResult, state);
        }

        List<String> issueKeys = collectAllMatchingIssueKeys(board, jqlFilter);

        if (issueKeys.isEmpty()) {
            return "No issues found matching the filter on board **%s**.".formatted(board.name());
        }

        String transitionId = resolveTransitionId(issueKeys.getFirst(), targetStatus);

        String confirmationMessage = "This will transition **%d** issue(s) on board **%s** to **%s**. Proceed?"
                .formatted(issueKeys.size(), board.name(), targetStatus);

        ElicitResult elicitResult = McpConfirmations.confirm(
                mcpSyncRequestContext, confirmationMessage,
                resolveElicitationMetadata(mcpSyncRequestContext)
        );

        return switch (elicitResult.action()) {
            case ACCEPT -> applyTransitions(issueKeys, transitionId, targetStatus, board);
            case DECLINE, CANCEL -> "Transition cancelled.";
        };
    }

    private String executeBatchedTransition(
            McpSyncRequestContext mcpSyncRequestContext,
            Board board,
            AgileQueryResult queryResult,
            AgileState state
    ) {
        String targetStatus = queryResult.targetStatus();
        String jqlFilter = emptyToNull(queryResult.jqlFilter());
        int batchSize = state.batchSize().orElse(DEFAULT_BATCH_SIZE);
        int estimatedCount = state.estimatedItemCount().orElse(0);
        int totalBatches = (int) Math.ceil((double) estimatedCount / batchSize);

        String confirmationMessage =
                "This will transition approximately **%d** issue(s) on board **%s** to **%s** in **%d** batches of %d. Proceed?"
                .formatted(estimatedCount, board.name(), targetStatus, totalBatches, batchSize);

        ElicitResult elicitResult = McpConfirmations.confirm(
                mcpSyncRequestContext, confirmationMessage,
                resolveElicitationMetadata(mcpSyncRequestContext)
        );

        return switch (elicitResult.action()) {
            case ACCEPT -> executeTransitionBatches(board, jqlFilter, targetStatus, batchSize, estimatedCount);
            case DECLINE, CANCEL -> "Transition cancelled.";
        };
    }

    private String executeTransitionBatches(
            Board board, String jqlFilter, String targetStatus, int batchSize, int estimatedCount
    ) {
        int totalBatches = (int) Math.ceil((double) estimatedCount / batchSize);
        long totalSuccessCount = 0;
        long totalFailureCount = 0;
        int startAt = 0;

        while (true) {
            int currentBatch = (startAt / batchSize) + 1;
            log.info("Processing transition batch {}/{} (startAt={})", currentBatch, totalBatches, startAt);

            JiraAgileTools.BoardIssuesRequest batchRequest = new JiraAgileTools.BoardIssuesRequest(
                    String.valueOf(board.id()),
                    jqlFilter,
                    startAt == 0 ? null : startAt,
                    batchSize, false
            );

            BoardIssues boardIssues = getBoardIssues(batchRequest);
            List<String> issueKeys = boardIssues.issues().stream().map(BoardIssue::key).toList();

            if (issueKeys.isEmpty()) {
                break;
            }

            String transitionId = resolveTransitionId(issueKeys.getFirst(), targetStatus);
            BatchTransitionResult batchResult = applyTransitionsAndCount(issueKeys, transitionId);
            totalSuccessCount += batchResult.successCount();
            totalFailureCount += batchResult.failureCount();

            int total = boardIssues.total() != null ? boardIssues.total() : 0;
            startAt += issueKeys.size();

            if (startAt >= total || issueKeys.size() < batchSize) {
                break;
            }
        }

        return buildBatchTransitionSummary(board, targetStatus, totalSuccessCount, totalFailureCount);
    }

    private String applyTransitions(
            List<String> issueKeys, String transitionId, String targetStatus, Board board
    ) {
        BatchTransitionResult result = applyTransitionsAndCount(issueKeys, transitionId);
        return buildBatchTransitionSummary(board, targetStatus, result.successCount(), result.failureCount());
    }

    private BatchTransitionResult applyTransitionsAndCount(List<String> issueKeys, String transitionId) {
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(TRANSITION_CONCURRENCY, issueKeys.size()));
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
                    }, executor))
                    .toList();

            long successCount = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Boolean::booleanValue)
                    .count();
            return new BatchTransitionResult(successCount, issueKeys.size() - successCount);
        } finally {
            executor.shutdown();
        }
    }

    private String buildBatchTransitionSummary(
            Board board, String targetStatus, long successCount, long failureCount
    ) {
        long totalProcessed = successCount + failureCount;
        StringBuilder summary = new StringBuilder(
                "**%s** — Transitioned **%d** of **%d** issues to **%s**."
                .formatted(board.name(), successCount, totalProcessed, targetStatus));
        if (failureCount > 0) {
            summary.append("\n\n**%d** issue(s) failed to transition.".formatted(failureCount));
        }
        return summary.toString();
    }

    private List<String> collectAllMatchingIssueKeys(Board board, String jqlFilter) {
        List<String> allKeys = new ArrayList<>();
        int currentStartAt = 0;

        while (true) {
            JiraAgileTools.BoardIssuesRequest request = new JiraAgileTools.BoardIssuesRequest(
                    String.valueOf(board.id()),
                    jqlFilter,
                    currentStartAt == 0 ? null : currentStartAt,
                    TRANSITION_FETCH_PAGE_SIZE, false
            );

            BoardIssues boardIssues = getBoardIssues(request);
            List<String> pageKeys = boardIssues.issues().stream().map(BoardIssue::key).toList();
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
                .orElseThrow(() -> new IllegalArgumentException(
                        "No transition named '%s' found on issue '%s'. Available: %s"
                        .formatted(targetStatus, sampleIssueKey,
                                transitions.transitions().stream().map(Transition::name).toList())));
    }

    private Map<String, Object> resolveElicitationMetadata(McpSyncRequestContext mcpSyncRequestContext) {
        Map<String, Object> requestMetadata = mcpSyncRequestContext.requestMeta();
        if (requestMetadata != null && requestMetadata.containsKey(CHAT_ID)) {
            return Map.of(CHAT_ID, requestMetadata.get(CHAT_ID).toString());
        }
        return Map.of(CHAT_ID, UUID.randomUUID().toString());
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    private record PagedQueryResult(List<BoardIssue> issues, int total, boolean hasMorePages, int nextStartAt) {}

    private record BatchTransitionResult(long successCount, long failureCount) {}
}
