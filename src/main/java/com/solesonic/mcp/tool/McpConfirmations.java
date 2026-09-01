package com.solesonic.mcp.tool;

import com.solesonic.mcp.exception.ToolFailures;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import java.util.Map;

public final class McpConfirmations {

    private static final Logger log = LoggerFactory.getLogger(McpConfirmations.class);

    private static final Map<String, Object> CONFIRMATION_SCHEMA = Map.of("type", "object", "properties", Map.of());

    private static final String CHAT_ID = "chatId";
    private static final String UNKNOWN_CHAT_ID = "unknown";

    private McpConfirmations() {
    }

    public static ElicitResult confirm(McpSyncRequestContext context, String message, Map<String, Object> meta) {
        return elicit(context, ElicitFormRequest.builder(message, CONFIRMATION_SCHEMA)
                .meta(meta)
                .build());
    }

    public static ElicitResult confirm(McpSyncRequestContext context, String message) {
        return elicit(context, ElicitFormRequest.builder(message, CONFIRMATION_SCHEMA)
                .build());
    }

    /**
     * Sends the confirmation prompt and turns any failure into something both the server log and
     * the calling model can act on.
     *
     * <p>A confirmation waits on a person, so the failure that matters here is the elicitation
     * timing out — {@code spring.ai.mcp.server.request-timeout} elapsing before the user answers.
     * The MCP session then discards the pending response, and the answer the user eventually
     * gives is rejected by the server with an HTTP 500 ("Unexpected response for unknown id"),
     * which the client can only report as a lost answer. Naming the prompt in the log, with the
     * chat id that correlates it to the client's own elicitation records, is what makes that
     * traceable end to end.
     */
    private static ElicitResult elicit(McpSyncRequestContext context, ElicitRequest elicitRequest) {
        try {
            return context.elicit(elicitRequest);
        } catch (Exception exception) {
            log.info("Confirmation prompt failed. chatId={} prompt=\"{}\"", chatId(elicitRequest.meta()), elicitRequest.message(), exception);

            throw ToolFailures.describe(
                    "Waiting for the user to confirm \"%s\"".formatted(elicitRequest.message()),
                    exception
            );
        }
    }

    private static String chatId(Map<String, Object> meta) {
        if (meta == null) {
            return UNKNOWN_CHAT_ID;
        }

        Object chatId = meta.get(CHAT_ID);

        if (chatId == null) {
            return UNKNOWN_CHAT_ID;
        }

        return chatId.toString();
    }
}
