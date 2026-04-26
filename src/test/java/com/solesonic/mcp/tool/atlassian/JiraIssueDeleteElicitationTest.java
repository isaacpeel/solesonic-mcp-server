package com.solesonic.mcp.tool.atlassian;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.mcp.annotation.context.DefaultMcpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JiraIssueDeleteElicitationTest {

    private McpSyncServerExchange exchange;

    private McpSyncRequestContext context;

    @BeforeEach
    public void setUp() {
        exchange = mock(McpSyncServerExchange.class);
        context = DefaultMcpSyncRequestContext.builder()
                .request(new CallToolRequest("delete_jira_issue", Map.of()))
                .exchange(exchange)
                .build();

        ClientCapabilities capabilities = mock(ClientCapabilities.class);
        ClientCapabilities.Elicitation elicitation = mock(ClientCapabilities.Elicitation.class);
        when(capabilities.elicitation()).thenReturn(elicitation);
        when(exchange.getClientCapabilities()).thenReturn(capabilities);
    }

    @Test
    public void testDeleteConfirmationElicitation_Accept() {
        ElicitResult expectedResult = mock(ElicitResult.class);
        when(expectedResult.action()).thenReturn(ElicitResult.Action.ACCEPT);
        when(exchange.createElicitation(any(ElicitRequest.class))).thenReturn(expectedResult);

        ElicitRequest confirmationRequest = ElicitRequest.builder()
                .message("Are you sure you want to delete Jira issue: PROJ-123?")
                .requestedSchema(Map.of("type", "object", "properties", Map.of()))
                .meta(Map.of(JiraIssueTools.CHAT_ID, "test-chat-id"))
                .build();

        ElicitResult result = context.elicit(confirmationRequest);

        assertThat(result.action()).isEqualTo(ElicitResult.Action.ACCEPT);

        ArgumentCaptor<ElicitRequest> captor = ArgumentCaptor.forClass(ElicitRequest.class);
        verify(exchange).createElicitation(captor.capture());
        ElicitRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.message()).isEqualTo("Are you sure you want to delete Jira issue: PROJ-123?");
        assertThat(capturedRequest.requestedSchema()).containsEntry("type", "object");
        assertThat(capturedRequest.requestedSchema()).containsKey("properties");
        assertThat(capturedRequest.meta()).containsEntry(JiraIssueTools.CHAT_ID, "test-chat-id");
    }

    @Test
    public void testDeleteConfirmationElicitation_Decline() {
        ElicitResult expectedResult = mock(ElicitResult.class);
        when(expectedResult.action()).thenReturn(ElicitResult.Action.DECLINE);
        when(exchange.createElicitation(any(ElicitRequest.class))).thenReturn(expectedResult);

        ElicitRequest confirmationRequest = ElicitRequest.builder()
                .message("Are you sure you want to delete Jira issue: PROJ-123?")
                .requestedSchema(Map.of("type", "object", "properties", Map.of()))
                .meta(Map.of(JiraIssueTools.CHAT_ID, "test-chat-id"))
                .build();

        ElicitResult result = context.elicit(confirmationRequest);

        assertThat(result.action()).isEqualTo(ElicitResult.Action.DECLINE);
    }

    @Test
    public void testDeleteConfirmationElicitation_Cancel() {
        ElicitResult expectedResult = mock(ElicitResult.class);
        when(expectedResult.action()).thenReturn(ElicitResult.Action.CANCEL);
        when(exchange.createElicitation(any(ElicitRequest.class))).thenReturn(expectedResult);

        ElicitRequest confirmationRequest = ElicitRequest.builder()
                .message("Are you sure you want to delete Jira issue: PROJ-123?")
                .requestedSchema(Map.of("type", "object", "properties", Map.of()))
                .meta(Map.of(JiraIssueTools.CHAT_ID, "test-chat-id"))
                .build();

        ElicitResult result = context.elicit(confirmationRequest);

        assertThat(result.action()).isEqualTo(ElicitResult.Action.CANCEL);
    }
}
