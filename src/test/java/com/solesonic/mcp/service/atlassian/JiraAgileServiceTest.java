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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class JiraAgileServiceTest {

    @Mock
    private WebClient webClient;

    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    private JiraAgileService service;

    @BeforeEach
    void setUp() {
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        service = new JiraAgileService(webClient);
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
    }

    @Test
    void listBoards_shouldReturnJson_fromApi() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.just(new Boards(List.of(new Board(1, "self", "Board 1", "scrum")), 50, 1, true)));

        ListBoardsRequest listBoardsRequest = new ListBoardsRequest(0, 50, null, null, null);
        Boards boards = service.listBoards(listBoardsRequest);

        assertNotNull(boards);
        assertTrue(CollectionUtils.isNotEmpty(boards.values()));
        verify(requestHeadersUriSpec).uri(any(Function.class));
    }

    @Test
    void getBoard_shouldReturnJson_fromApi() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.just(new Board(1, "self", "Board", "scrum")));

        Board board = service.getBoard("1");

        assertNotNull(board);
        assertEquals(1, board.id());
        verify(requestHeadersUriSpec).uri(any(Function.class));
    }
}
