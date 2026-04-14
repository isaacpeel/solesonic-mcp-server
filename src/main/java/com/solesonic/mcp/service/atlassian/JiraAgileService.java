package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.model.atlassian.agile.Board;
import com.solesonic.mcp.model.atlassian.agile.BoardIssue;
import com.solesonic.mcp.model.atlassian.agile.BoardIssues;
import com.solesonic.mcp.model.atlassian.agile.Boards;
import com.solesonic.mcp.model.atlassian.jira.Content;
import com.solesonic.mcp.model.atlassian.jira.Description;
import com.solesonic.mcp.model.atlassian.jira.Fields;
import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import com.solesonic.mcp.model.atlassian.jira.TextContent;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import com.solesonic.mcp.workflow.agile.AgileQueryResult;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.mcp.service.atlassian.AtlassianConstants.*;

@Service
public class JiraAgileService {
    private static final Logger log = LoggerFactory.getLogger(JiraAgileService.class);

    private static final int ISSUE_FETCH_CONCURRENCY = 5;
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

    private record PagedQueryResult(String formattedContent, boolean hasMorePages, int nextStartAt) {}

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

    public JiraAgileService(
            @Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient,
            JiraIssueService jiraIssueService,
            ChatClient chatClient
    ) {
        this.webClient = webClient;
        this.jiraIssueService = jiraIssueService;
        this.chatClient = chatClient;
    }

    public Mono<Boards> listBoards(JiraAgileTools.ListBoardsRequest listBoardsRequest) {
        log.info("Listing Jira boards");

        String[] baseUri = {EX, JIRA, cloudIdPath, REST_PATH, AGILE_PATH, AGILE_VERSION_PATH, BOARD_PATH};

        return webClient.get()
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
                .doOnSuccess(_ -> log.info("Jira boards retrieved successfully"));
    }

    public Mono<Board> getBoard(String boardId) {
        log.debug("Getting Jira board: {}", boardId);

        String[] base = {EX, JIRA, cloudIdPath, REST_PATH, AGILE_PATH, AGILE_VERSION_PATH, BOARD_PATH, boardId};

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(base)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(Board.class))
                .doOnSuccess(_ -> log.info("Jira board retrieved successfully: {}", boardId));
    }

    public Mono<BoardIssues> getBoardIssues(JiraAgileTools.BoardIssuesRequest boardIssuesRequest) {
        String boardId = boardIssuesRequest.boardId();
        log.info("Getting Jira board issues for board ID: {}", boardId);

        String[] base = {EX, JIRA, cloudIdPath, REST_PATH, AGILE_PATH, AGILE_VERSION_PATH, BOARD_PATH, boardId, ISSUE_PATH};

        return webClient.get()
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
                .doOnSuccess(boardIssues -> {
                    assert boardIssues != null;
                    List<BoardIssue> issues = boardIssues.issues();
                    log.info("Found {} issues", issues.size());
                });
    }

    public Mono<String> handleBoardSelection(
            McpAsyncRequestContext mcpAsyncRequestContext,
            AgileQueryWorkflowContext workflowContext
    ) {
        List<Board> boards = workflowContext.getBoards();
        AgileQueryResult agileQueryResult = workflowContext.getAgileQueryResult();
        String userMessage = workflowContext.getOriginalUserMessage();

        if (boards.isEmpty()) {
            return Mono.just("No accessible Jira boards were found.");
        }

        if (boards.size() == 1) {
            Board selectedBoard = boards.getFirst();
            log.info("Single board found, auto-selecting: {} (ID: {})", selectedBoard.name(), selectedBoard.id());
            return executePagedBoardQuery(mcpAsyncRequestContext, selectedBoard, agileQueryResult, userMessage, agileQueryResult.resolvedStartAt());
        }

        String boardListMessage = buildBoardSelectionMessage(boards);
        log.info("Multiple boards found ({}), eliciting selection", boards.size());

        return mcpAsyncRequestContext.elicit(
                elicit -> elicit.message(boardListMessage),
                JiraAgileTools.BoardSelectionInput.class
        ).flatMap(elicitResult -> switch (elicitResult.action()) {
            case ACCEPT -> {
                String selectedBoardId = elicitResult.structuredContent().boardId();

                yield boards.stream()
                        .filter(board -> String.valueOf(board.id()).equals(selectedBoardId))
                        .findFirst()
                        .map(board -> executePagedBoardQuery(mcpAsyncRequestContext, board, agileQueryResult, userMessage, agileQueryResult.resolvedStartAt()))
                        .orElseGet(() -> Mono.just(
                                "Board with ID '" + selectedBoardId + "' was not found in the available boards."
                        ));
            }
            case DECLINE, CANCEL -> Mono.just("Board selection was cancelled.");
        });
    }

    private Mono<String> executePagedBoardQuery(
            McpAsyncRequestContext mcpAsyncRequestContext,
            Board board,
            AgileQueryResult agileQueryResult,
            String userMessage,
            int startAt
    ) {
        return fetchAndFormatPage(board, agileQueryResult, userMessage, startAt)
                .flatMap(pagedResult -> {
                    if (!pagedResult.hasMorePages()) {
                        return Mono.just(pagedResult.formattedContent());
                    }

                    String elicitMessage = "Showing issues %d–%d. Would you like to load the next page?"
                            .formatted(startAt + 1, pagedResult.nextStartAt());

                    return mcpAsyncRequestContext.elicit(
                            elicit -> elicit.message(elicitMessage),
                            JiraAgileTools.LoadMoreInput.class
                    ).flatMap(elicitResult -> switch (elicitResult.action()) {
                        case ACCEPT -> executePagedBoardQuery(
                                mcpAsyncRequestContext,
                                board,
                                agileQueryResult,
                                userMessage,
                                pagedResult.nextStartAt()
                        );
                        case DECLINE, CANCEL -> Mono.just(pagedResult.formattedContent());
                    }).onErrorResume(error -> {
                        log.warn("Load more elicitation unavailable, returning current page: {}", error.getMessage());
                        return Mono.just(pagedResult.formattedContent());
                    });
                });
    }

    private Mono<PagedQueryResult> fetchAndFormatPage(
            Board board,
            AgileQueryResult agileQueryResult,
            String userMessage,
            int startAt
    ) {
        String jqlFilter = agileQueryResult.jqlFilter() == null ? "" : agileQueryResult.jqlFilter().strip();
        log.info("Fetching page for board '{}' (ID: {}) startAt={} with JQL: '{}'", board.name(), board.id(), startAt, jqlFilter);

        JiraAgileTools.BoardIssuesRequest boardIssuesRequest = new JiraAgileTools.BoardIssuesRequest(
                String.valueOf(board.id()),
                jqlFilter.isEmpty() ? null : jqlFilter,
                startAt == 0 ? null : startAt,
                DEFAULT_PAGE_SIZE,
                false
        );

        return getBoardIssues(boardIssuesRequest)
                .flatMap(boardIssues -> {
                    if (agileQueryResult.isCountQuery()) {
                        return Mono.just(new PagedQueryResult(
                                formatCountResult(board, boardIssues, agileQueryResult),
                                false,
                                0
                        ));
                    }

                    int total = boardIssues.total() != null ? boardIssues.total() : boardIssues.issues().size();
                    int fetchedCount = boardIssues.issues().size();
                    int nextStartAt = startAt + fetchedCount;
                    boolean hasMorePages = nextStartAt < total;

                    return enrichAndFormatIssueList(board, boardIssues, userMessage, fetchedCount, total)
                            .map(formattedContent -> {
                                if (hasMorePages) {
                                    String pageNote = "\n\n> Showing issues %d–%d of %d. Ask to see more to continue."
                                            .formatted(startAt + 1, nextStartAt, total);
                                    return new PagedQueryResult(formattedContent + pageNote, true, nextStartAt);
                                }
                                return new PagedQueryResult(formattedContent, false, 0);
                            });
                });
    }

    private Mono<String> enrichAndFormatIssueList(
            Board board,
            BoardIssues boardIssues,
            String userMessage,
            int shownCount,
            int total
    ) {
        if (boardIssues.issues().isEmpty()) {
            return Mono.just("**%s** — No issues found.".formatted(board.name()));
        }

        List<String> issueKeys = boardIssues.issues().stream()
                .map(BoardIssue::key)
                .toList();

        log.info("Fetching full details for {} issue(s) on board '{}'", issueKeys.size(), board.name());

        return Flux.fromIterable(issueKeys)
                .flatMap(issueKey -> jiraIssueService.get(issueKey)
                        .onErrorResume(error -> {
                            log.warn("Failed to fetch details for issue {}: {}", issueKey, error.getMessage());
                            return Mono.empty();
                        }), ISSUE_FETCH_CONCURRENCY)
                .collectList()
                .flatMap(fullIssues -> {
                    String issueData = buildIssueDataForLlm(fullIssues);
                    String prompt = ISSUE_ENRICHMENT_PROMPT_TEMPLATE.formatted(
                            userMessage, board.name(), shownCount, total, issueData
                    );
                    log.info("Requesting LLM enrichment for {} issue(s)", fullIssues.size());
                    return Mono.fromCallable(() -> chatClient.prompt().user(prompt).call().content())
                            .subscribeOn(Schedulers.boundedElastic());
                });
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

    private String formatCountResult(Board board, BoardIssues boardIssues, AgileQueryResult agileQueryResult) {
        int total = boardIssues.total() != null ? boardIssues.total() : boardIssues.issues().size();
        boolean noFilter = agileQueryResult.jqlFilter() == null || agileQueryResult.jqlFilter().isBlank();
        String jqlDescription = noFilter
                ? "all issues"
                : "issues matching `" + agileQueryResult.jqlFilter() + "`";
        return "**%s** — %d %s (%s)".formatted(board.name(), total, total == 1 ? "issue" : "issues", jqlDescription);
    }

    public String buildBoardSelectionMessage(List<Board> boards) {
        StringBuilder message = new StringBuilder();
        message.append("Multiple Jira boards are available. Please enter the ID of the board you'd like to query:\n\n");

        boards.forEach(board ->
                message.append("- **").append(board.name()).append("** — ID: `").append(board.id())
                        .append("`, Type: ").append(board.type()).append("\n")
        );

        return message.toString();
    }
}
