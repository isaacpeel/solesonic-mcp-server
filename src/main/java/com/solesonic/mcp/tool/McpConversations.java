package com.solesonic.mcp.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import java.util.Map;
import java.util.UUID;

/**
 * Reads the conversation an MCP tool call belongs to from the request meta's {@code chatId}.
 */
public final class McpConversations {
    private static final Logger log = LoggerFactory.getLogger(McpConversations.class);

    public static final String CHAT_ID = "chatId";

    private McpConversations() {
    }

    /**
     * The caller's conversation id, or a fresh one when the client sent none — in which case anything
     * keyed by it (a paused graph run, say) cannot be picked up again by a later call.
     */
    public static String conversationId(McpSyncRequestContext mcpSyncRequestContext) {
        Map<String, Object> requestMeta = mcpSyncRequestContext.requestMeta();

        if (requestMeta != null && requestMeta.get(CHAT_ID) != null) {
            return requestMeta.get(CHAT_ID).toString();
        }

        String generatedConversationId = UUID.randomUUID().toString();
        log.warn("MCP request carried no {}; using one-off conversation id {}", CHAT_ID, generatedConversationId);

        return generatedConversationId;
    }
}
