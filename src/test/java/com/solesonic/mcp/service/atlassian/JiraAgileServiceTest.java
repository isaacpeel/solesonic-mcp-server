package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.model.atlassian.agile.Board;
import com.solesonic.mcp.model.atlassian.agile.Boards;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools.ListBoardsRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class JiraAgileServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private JiraIssueService jiraIssueService;

    @Mock
    private ChatClient chatClient;

    private JiraAgileService service;

    @BeforeEach
    void setUp() {
        service = new JiraAgileService(webClient, jiraIssueService, chatClient);
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
        ReflectionTestUtils.setField(service, "jiraUrlTemplate", "https://test.atlassian.net/browse/{key}");
    }

    @Test
    void listBoards_shouldReturnBoards_fromApi() {
        Boards expectedBoards = new Boards(List.of(new Board(1, "self", "Board 1", "scrum")), 50, 1, true);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(expectedBoards));

        ListBoardsRequest listBoardsRequest = new ListBoardsRequest(0, 50, null, null, null);
        Boards boards = service.listBoards(listBoardsRequest);

        assertNotNull(boards);
        assertTrue(CollectionUtils.isNotEmpty(boards.values()));
        verify(requestHeadersUriSpec).uri(any(Function.class));
    }

    @Test
    void getBoard_shouldReturnBoard_fromApi() {
        Board expectedBoard = new Board(1, "self", "Board", "scrum");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(expectedBoard));

        Board board = service.getBoard("1");

        assertNotNull(board);
        assertEquals(1, board.id());
        verify(requestHeadersUriSpec).uri(any(Function.class));
    }
}
