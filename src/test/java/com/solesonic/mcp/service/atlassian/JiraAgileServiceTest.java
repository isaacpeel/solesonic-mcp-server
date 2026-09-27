package com.solesonic.mcp.service.atlassian;

import com.solesonic.agent.agile.AgileQueryIntent;
import com.solesonic.agent.agile.AgileState;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.agile.Board;
import com.solesonic.model.atlassian.agile.BoardColumn;
import com.solesonic.model.atlassian.agile.BoardConfiguration;
import com.solesonic.model.atlassian.agile.Boards;
import com.solesonic.model.atlassian.agile.ColumnConfig;
import com.solesonic.model.atlassian.agile.ColumnStatus;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools.ListBoardsRequest;
import com.solesonic.service.atlassian.JiraAgileService;
import com.solesonic.service.atlassian.JiraIssueService;
import com.solesonic.testsupport.RecordingExchangeFunction;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.solesonic.testsupport.RecordingExchangeFunction.callerIdentityOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JiraAgileServiceTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private JiraIssueService jiraIssueService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private McpSyncRequestContext mcpSyncRequestContext;

    private RecordingExchangeFunction backend;
    private JiraAgileService service;

    @BeforeEach
    void setUp() {
        backend = new RecordingExchangeFunction();
        service = new JiraAgileService(backend.webClient(), jiraIssueService, chatClient);
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
    }

    @Test
    void listBoards_withRequest_shouldReturnBoards_andCarryTheCaller() {
        backend.respondWithJson(jsonMapper.writeValueAsString(
                new Boards(List.of(new Board(1, "self", "Board 1", "scrum")), 50, 1, true)));

        ListBoardsRequest listBoardsRequest = new ListBoardsRequest(0, 50, null, null, null);
        Boards boards = service.listBoards(CALLER, listBoardsRequest);

        assertNotNull(boards);
        assertTrue(CollectionUtils.isNotEmpty(boards.values()));
        assertEquals(Optional.of(CALLER), callerIdentityOf(backend.onlyRequest()));
    }

    @Test
    void listBoards_shouldReturnBoards_andCarryTheCaller() {
        backend.respondWithJson(jsonMapper.writeValueAsString(
                new Boards(List.of(new Board(1, "self", "Board 1", "scrum")), 50, 1, true)));

        Boards boards = service.listBoards(CALLER);

        assertEquals(1, boards.values().size());
        assertTrue(backend.onlyRequest().url().getPath().endsWith("/rest/agile/1.0/board"));
        assertEquals(Optional.of(CALLER), callerIdentityOf(backend.onlyRequest()));
    }

    @Test
    void getBoard_shouldReturnBoard_andCarryTheCaller() {
        backend.respondWithJson(jsonMapper.writeValueAsString(new Board(1, "self", "Board", "scrum")));

        Board board = service.getBoard(CALLER, "1");

        assertNotNull(board);
        assertEquals(1, board.id());
        assertEquals(Optional.of(CALLER), callerIdentityOf(backend.onlyRequest()));
    }

    @Test
    void getBoardConfiguration_shouldReturnConfiguration_andCarryTheCaller() {
        BoardConfiguration expected = new BoardConfiguration(1, "My Board", "scrum",
                new ColumnConfig(List.of(new BoardColumn("To Do", List.of(new ColumnStatus("1", "self/1")))), "issueCount"));
        backend.respondWithJson(jsonMapper.writeValueAsString(expected));

        BoardConfiguration configuration = service.getBoardConfiguration(CALLER, "1");

        assertNotNull(configuration);
        assertEquals("1", configuration.columnConfig().columns().getFirst().statuses().getFirst().id());
        assertEquals(Optional.of(CALLER), callerIdentityOf(backend.onlyRequest()));
    }

    @Test
    void handleTransitionQuery_withNoScope_refusesWithoutCallingApi() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryIntent queryResult = new AgileQueryIntent(
                List.of(), null, null, null, "", "TRANSITION", 0, "Done");
        AgileState state = new AgileState(Map.of());

        String result = service.handleTransitionQuery(CALLER, mcpSyncRequestContext, board, queryResult, state);

        assertTrue(result.contains("Refusing to transition every issue on the board"));
        assertTrue(backend.requests().isEmpty());
        verifyNoInteractions(jiraIssueService);
    }
}
