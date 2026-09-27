package com.solesonic.mcp.service.atlassian;

import com.solesonic.agent.agile.AgileQueryIntent;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.agile.Board;
import com.solesonic.model.atlassian.agile.BoardColumn;
import com.solesonic.model.atlassian.agile.BoardConfiguration;
import com.solesonic.model.atlassian.agile.BoardIssue;
import com.solesonic.model.atlassian.agile.BoardIssues;
import com.solesonic.model.atlassian.agile.ColumnConfig;
import com.solesonic.model.atlassian.agile.ColumnStatus;
import com.solesonic.model.atlassian.jira.JiraIssue;
import com.solesonic.service.atlassian.JiraAgileService;
import com.solesonic.service.atlassian.JiraIssueService;
import com.solesonic.testsupport.RecordingExchangeFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.solesonic.testsupport.RecordingExchangeFunction.callerIdentityOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraAgileServiceHelperTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private JiraIssueService jiraIssueService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private McpSyncRequestContext mcpSyncRequestContext;

    private RecordingExchangeFunction backend;
    private JiraAgileService service;

    @BeforeEach
    void setUp() {
        backend = new RecordingExchangeFunction();
        service = new JiraAgileService(backend.webClient(), jiraIssueService, chatClient);
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
        ReflectionTestUtils.setField(service, "jiraUrlTemplate", "https://example.atlassian.net/browse/{key}");
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
        AgileQueryIntent queryResult = new AgileQueryIntent(List.of(), null, null, null, "", "COUNT", null, null);
        respondWith(boardConfigurationWithNoColumns(), boardIssuesWithTotal(5));

        String result = service.handleCountQuery(CALLER, board, queryResult);

        assertThat(result).contains("all issues").contains("5").contains("My Board");
    }

    @Test
    void handleCountQuery_carriesTheCallerOnTheConfigurationAndIssueCalls() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryIntent queryResult = new AgileQueryIntent(List.of(), null, null, null, "", "COUNT", null, null);
        respondWith(boardConfigurationWithNoColumns(), boardIssuesWithTotal(5));

        service.handleCountQuery(CALLER, board, queryResult);

        assertThat(backend.requests()).hasSize(2);
        assertThat(backend.requests()).allSatisfy(request -> assertThat(callerIdentityOf(request)).contains(CALLER));
    }

    @Test
    void handleCountQuery_withJqlFilter_includesFilter() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryIntent queryResult = new AgileQueryIntent(List.of(), null, null, null, "status = Done", "COUNT", null, null);
        respondWith(boardIssuesWithTotal(3));

        String result = service.handleCountQuery(CALLER, board, queryResult);

        assertThat(result).contains("status = Done").contains("3");
    }

    @Test
    void handleCountQuery_singleIssue_usesSingularWord() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryIntent queryResult = new AgileQueryIntent(List.of(), null, null, null, "", "COUNT", null, null);
        respondWith(boardConfigurationWithNoColumns(), boardIssuesWithTotal(1));

        String result = service.handleCountQuery(CALLER, board, queryResult);

        assertThat(result).contains("1 issue").doesNotContain("1 issues");
    }

    @Test
    void handleCountQuery_zeroIssues_usesPlural() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryIntent queryResult = new AgileQueryIntent(List.of(), null, null, null, "", "COUNT", null, null);
        respondWith(boardConfigurationWithNoColumns(), boardIssuesWithTotal(0));

        String result = service.handleCountQuery(CALLER, board, queryResult);

        assertThat(result).contains("0 issues");
    }

    @Test
    void handleCountQuery_noExplicitScope_scopesToBoardVisibleColumns() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryIntent queryResult = new AgileQueryIntent(List.of(), null, null, null, "", "COUNT", null, null);
        respondWith(boardConfigurationWithColumns("1", "3", "10001"), boardIssuesWithTotal(11));

        String result = service.handleCountQuery(CALLER, board, queryResult);

        assertThat(result).contains("status in (1, 3, 10001)").contains("11").doesNotContain("all issues");
    }

    @Test
    void handleListQuery_someIssuesFailToFetch_notesThePartialFailureForTheUser() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryIntent queryResult = new AgileQueryIntent(
                List.of(), "currentUser()", null, null, "", "LIST", null, null);
        respondWith(boardIssuesWithKeys("IB-1", "IB-2"));

        when(jiraIssueService.get(CALLER, "IB-1")).thenReturn(JiraIssue.fields(null).key("IB-1").build());
        when(jiraIssueService.get(CALLER, "IB-2")).thenThrow(new RuntimeException("boom"));

        stubChatResponse();

        String result = service.handleListQuery(CALLER, mcpSyncRequestContext, board, queryResult, "list my issues");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientRequestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("Note: 1 of 2 issues could not be retrieved and are omitted below.");
        assertThat(result).isEqualTo("formatted response");
    }

    @Test
    void handleListQuery_allIssuesFetchSuccessfully_omitsTheFailureNote() {
        Board board = new Board(1, "self", "My Board", "scrum");
        AgileQueryIntent queryResult = new AgileQueryIntent(
                List.of(), "currentUser()", null, null, "", "LIST", null, null);
        respondWith(boardIssuesWithKeys("IB-1"));

        when(jiraIssueService.get(CALLER, "IB-1")).thenReturn(JiraIssue.fields(null).key("IB-1").build());

        stubChatResponse();

        service.handleListQuery(CALLER, mcpSyncRequestContext, board, queryResult, "list my issues");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientRequestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).doesNotContain("and are omitted below");
    }

    private void stubChatResponse() {
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("formatted response");
    }

    /**
     * Queues the Jira responses in call order. A blank-scope {@link AgileQueryIntent} triggers a
     * board-configuration lookup before the board-issues call.
     */
    private void respondWith(Object... responseBodies) {
        for (Object responseBody : responseBodies) {
            backend.respondWithJson(jsonMapper.writeValueAsString(responseBody));
        }
    }

    private BoardIssues boardIssuesWithKeys(String... keys) {
        List<BoardIssue> issues = Stream.of(keys)
                .map(key -> new BoardIssue(key, "self/" + key, key))
                .toList();
        return new BoardIssues(null, 0, issues.size(), issues.size(), issues);
    }

    private BoardIssues boardIssuesWithTotal(int total) {
        return new BoardIssues(null, 0, 0, total, List.of());
    }

    private BoardConfiguration boardConfigurationWithNoColumns() {
        return new BoardConfiguration(1, "My Board", "scrum", new ColumnConfig(List.of(), null));
    }

    @SuppressWarnings("all")
    private BoardConfiguration boardConfigurationWithColumns(String... statusIds) {
        List<ColumnStatus> statuses = Stream.of(statusIds)
                .map(statusId -> new ColumnStatus(statusId, "self/" + statusId))
                .toList();
        BoardColumn column = new BoardColumn("Column", statuses);
        return new BoardConfiguration(1, "My Board", "scrum", new ColumnConfig(List.of(column), null));
    }
}
