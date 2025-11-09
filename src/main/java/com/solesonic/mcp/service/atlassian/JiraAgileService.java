package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.model.atlassian.agile.Board;
import com.solesonic.mcp.model.atlassian.agile.BoardIssue;
import com.solesonic.mcp.model.atlassian.agile.BoardIssues;
import com.solesonic.mcp.model.atlassian.agile.Boards;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    public static final String ACCOUNT_ID = "accountId";
    public static final String JQL = "jql";
    public static final String VALIDATE_QUERY = "validateQuery";

    @Value("${solesonic.llm.jira.cloud.id.path}")
    private String cloudIdPath;

    private final WebClient webClient;

    public JiraAgileService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
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
}
