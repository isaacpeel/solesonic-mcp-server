package com.solesonic.mcp.tool;

import com.solesonic.mcp.exception.McpToolFailureException;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.mcp.annotation.context.DefaultMcpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link McpConfirmations}.
 *
 * <p>The failure cases here stand in for the elicitation timeout that produced the delivery
 * failure recorded in {@code ai-scratch/agile-tool/user-story-elicitation-delivery-failure.txt}:
 * once {@code spring.ai.mcp.server.request-timeout} elapses, {@code context.elicit(...)} throws
 * and the caller has to be told something it can act on.
 */
class McpConfirmationsTest {

    private static final String PROMPT = "Are you sure you want to delete Jira issue: PROJ-123?";
    private static final String CHAT_ID = "chatId";

    private McpSyncServerExchange exchange;

    private McpSyncRequestContext context;

    @BeforeEach
    void setUp() {
        exchange = mock(McpSyncServerExchange.class);

        context = DefaultMcpSyncRequestContext.builder()
                .request(CallToolRequest.builder("delete_jira_issue").build())
                .exchange(exchange)
                .build();

        ClientCapabilities capabilities = mock(ClientCapabilities.class);
        when(capabilities.elicitation()).thenReturn(mock(ClientCapabilities.Elicitation.class));
        when(exchange.getClientCapabilities()).thenReturn(capabilities);
    }

    @Test
    void confirmSendsThePromptWithItsSchemaAndMeta() {
        ElicitResult accepted = mock(ElicitResult.class);
        when(accepted.action()).thenReturn(ElicitResult.Action.ACCEPT);
        when(exchange.createElicitation(any(ElicitRequest.class))).thenReturn(accepted);

        ElicitResult result = McpConfirmations.confirm(context, PROMPT, Map.of(CHAT_ID, "test-chat-id"));

        assertThat(result.action()).isEqualTo(ElicitResult.Action.ACCEPT);

        ArgumentCaptor<ElicitFormRequest> captor = ArgumentCaptor.forClass(ElicitFormRequest.class);
        verify(exchange).createElicitation(captor.capture());
        ElicitFormRequest sentRequest = captor.getValue();

        assertThat(sentRequest.message()).isEqualTo(PROMPT);
        assertThat(sentRequest.requestedSchema()).containsEntry("type", "object");
        assertThat(sentRequest.requestedSchema()).containsKey("properties");
        assertThat(sentRequest.meta()).containsEntry(CHAT_ID, "test-chat-id");
    }

    @Test
    void confirmWithoutMetaStillSendsThePrompt() {
        ElicitResult declined = mock(ElicitResult.class);
        when(declined.action()).thenReturn(ElicitResult.Action.DECLINE);
        when(exchange.createElicitation(any(ElicitRequest.class))).thenReturn(declined);

        ElicitResult result = McpConfirmations.confirm(context, PROMPT);

        assertThat(result.action()).isEqualTo(ElicitResult.Action.DECLINE);

        ArgumentCaptor<ElicitFormRequest> captor = ArgumentCaptor.forClass(ElicitFormRequest.class);
        verify(exchange).createElicitation(captor.capture());
        assertThat(captor.getValue().message()).isEqualTo(PROMPT);
        assertThat(captor.getValue().meta()).isNull();
    }

    @Test
    void elicitationTimeoutFailsWithAMessageNamingThePrompt() {
        when(exchange.createElicitation(any(ElicitRequest.class)))
                .thenThrow(new RuntimeException(new TimeoutException(
                        "Did not observe any item or terminal signal within 20000ms")));

        assertThatThrownBy(() -> McpConfirmations.confirm(context, PROMPT, Map.of(CHAT_ID, "test-chat-id")))
                .isInstanceOf(McpToolFailureException.class)
                .hasMessageContaining("Waiting for the user to confirm")
                .hasMessageContaining(PROMPT)
                .hasMessageContaining("Did not observe any item or terminal signal within 20000ms");
    }

    @Test
    void messagelessFailureStillProducesAnActionableMessage() {
        when(exchange.createElicitation(any(ElicitRequest.class)))
                .thenThrow(new RuntimeException(new TimeoutException()));

        assertThatThrownBy(() -> McpConfirmations.confirm(context, PROMPT))
                .isInstanceOf(McpToolFailureException.class)
                .hasMessageContaining(TimeoutException.class.getName())
                .hasMessageNotContaining("null");
    }

    @Test
    void aClientWithoutElicitationSupportFailsWithAnActionableMessage() {
        when(exchange.getClientCapabilities()).thenReturn(mock(ClientCapabilities.class));

        assertThatThrownBy(() -> McpConfirmations.confirm(context, PROMPT))
                .isInstanceOf(McpToolFailureException.class)
                .hasMessageContaining("Elicitation not supported by the client");
    }
}
