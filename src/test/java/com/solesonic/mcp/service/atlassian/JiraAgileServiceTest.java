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
import reactor.test.StepVerifier;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class JiraAgileServiceTest {
    @Mock
    private WebClient webClient;

    @Mock
    private JiraIssueService jiraIssueService;

    @Mock
    private ChatClient chatClient;

    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    private JiraAgileService service;

    @BeforeEach
    void setUp() {
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        service = new JiraAgileService(webClient, jiraIssueService, chatClient);
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
        ReflectionTestUtils.setField(service, "jiraUrlTemplate", "https://test.atlassian.net/browse/{key}");
    }


    @Test
    void listBoards_shouldReturnJson_fromApi() {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        doReturn(Mono.just(new Boards(List.of(new Board(1, "self", "Board 1", "scrum")), 50, 1, true)))
                .when(requestHeadersSpec).exchangeToMono(any());

        ListBoardsRequest listBoardsRequest = new ListBoardsRequest(0, 50, null, null, null);
        StepVerifier.create(service.listBoards(listBoardsRequest))
                .assertNext(boards -> {
                    org.junit.jupiter.api.Assertions.assertNotNull(boards);
                    org.junit.jupiter.api.Assertions.assertTrue(CollectionUtils.isNotEmpty(boards.values()));
                })
                .verifyComplete();

        verify(requestHeadersUriSpec).uri(any(Function.class));
    }

    @Test
    void getBoard_shouldReturnJson_fromApi() {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        doReturn(Mono.just(new Board(1, "self", "Board", "scrum")))
                .when(requestHeadersSpec).exchangeToMono(any());

        StepVerifier.create(service.getBoard("1"))
                .assertNext(board -> {
                    org.junit.jupiter.api.Assertions.assertNotNull(board);
                    assertEquals(1, board.id());
                })
                .verifyComplete();

        verify(requestHeadersUriSpec).uri(any(Function.class));
    }
}
