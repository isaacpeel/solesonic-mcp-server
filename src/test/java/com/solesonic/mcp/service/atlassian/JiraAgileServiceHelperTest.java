package com.solesonic.mcp.service.atlassian;

import com.solesonic.agent.agile.AgileQueryResult;
import com.solesonic.mcp.model.atlassian.agile.Board;
import com.solesonic.mcp.model.atlassian.agile.BoardIssues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class JiraAgileServiceHelperTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private JiraIssueService jiraIssueService;

    @Mock
    private ChatClient chatClient;

    private JiraAgileService service;

    @BeforeEach
    void setUp() {
        service = new JiraAgileService(webClient, jiraIssueService, chatClient);
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
    }

    @Test
    void buildBoardSelectionMessage_singleBoard_formatsCorrectly() {
        Board board = new Board(42, "self", "Engineering Board", "scrum");

        String result = service.buildBoardSelectionMessage(List.of(board));

        assertThat(result).contains("Engineering Board").contains("42").contains("scrum");
    }

    @Test
    void buildBoardSelectionMessage_multipleBoards_listsAll() {
        Board firstBoard = new Board(1, "self-1", "Alpha Board", "scrum");
        Board secondBoard = new Board(2, "self-2", "Beta Board", "kanban");

        String result = service.buildBoardSelectionMessage(List.of(firstBoard, secondBoard));

        assertThat(result).contains("Alpha Board").contains("Beta Board");
        assertThat(result.indexOf("Alpha Board")).isLessThan(result.indexOf("Beta Board"));
    }

    @Test
    void handleCountQuery_noJqlFilter_saysAllIssues() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryResult queryResult = new AgileQueryResult("", "COUNT", null, null);
        stubBoardIssues(boardIssuesWithTotal(5));

        String result = service.handleCountQuery(board, queryResult);

        assertThat(result).contains("all issues").contains("5").contains("My Board");
    }

    @Test
    void handleCountQuery_withJqlFilter_includesFilter() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryResult queryResult = new AgileQueryResult("status = Done", "COUNT", null, null);
        stubBoardIssues(boardIssuesWithTotal(3));

        String result = service.handleCountQuery(board, queryResult);

        assertThat(result).contains("status = Done").contains("3");
    }

    @Test
    void handleCountQuery_singleIssue_usesSingularWord() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryResult queryResult = new AgileQueryResult("", "COUNT", null, null);
        stubBoardIssues(boardIssuesWithTotal(1));

        String result = service.handleCountQuery(board, queryResult);

        assertThat(result).contains("1 issue").doesNotContain("1 issues");
    }

    @Test
    void handleCountQuery_zeroIssues_usesPlural() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryResult queryResult = new AgileQueryResult("", "COUNT", null, null);
        stubBoardIssues(boardIssuesWithTotal(0));

        String result = service.handleCountQuery(board, queryResult);

        assertThat(result).contains("0 issues");
    }

    private void stubBoardIssues(BoardIssues boardIssues) {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec)
                .uri(ArgumentMatchers.<Function<UriBuilder, URI>>any());
        doReturn(Mono.just(boardIssues)).when(requestHeadersSpec).exchangeToMono(ArgumentMatchers.any());
    }

    private BoardIssues boardIssuesWithTotal(int total) {
        return new BoardIssues(null, 0, 0, total, List.of());
    }
}
