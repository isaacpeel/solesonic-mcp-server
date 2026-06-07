package com.solesonic.mcp.service.atlassian;

import com.solesonic.model.atlassian.agile.Board;
import com.solesonic.model.atlassian.agile.Boards;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools.ListBoardsRequest;
import com.solesonic.service.atlassian.JiraAgileService;
import com.solesonic.service.atlassian.JiraIssueService;
import org.apache.commons.collections4.CollectionUtils;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraAgileServiceTest {

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
    void listBoards_shouldReturnBoards_fromApi() {
        Boards expectedBoards = new Boards(List.of(new Board(1, "self", "Board 1", "scrum")), 50, 1, true);

        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(ArgumentMatchers.<Function<UriBuilder, URI>>any());
        doReturn(Mono.just(expectedBoards)).when(requestHeadersSpec).exchangeToMono(ArgumentMatchers.any());

        ListBoardsRequest listBoardsRequest = new ListBoardsRequest(0, 50, null, null, null);
        Boards boards = service.listBoards(listBoardsRequest);

        assertNotNull(boards);
        assertTrue(CollectionUtils.isNotEmpty(boards.values()));
        verify(requestHeadersUriSpec).uri(ArgumentMatchers.<Function<UriBuilder, URI>>any());
    }

    @Test
    void getBoard_shouldReturnBoard_fromApi() {
        Board expectedBoard = new Board(1, "self", "Board", "scrum");

        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(ArgumentMatchers.<Function<UriBuilder, URI>>any());
        doReturn(Mono.just(expectedBoard)).when(requestHeadersSpec).exchangeToMono(ArgumentMatchers.any());

        Board board = service.getBoard("1");

        assertNotNull(board);
        assertEquals(1, board.id());
        verify(requestHeadersUriSpec).uri(ArgumentMatchers.<Function<UriBuilder, URI>>any());
    }
}
