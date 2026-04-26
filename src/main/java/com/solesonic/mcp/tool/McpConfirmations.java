package com.solesonic.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import java.util.Map;

public final class McpConfirmations {

    private static final Map<String, Object> CONFIRMATION_SCHEMA = Map.of("type", "object", "properties", Map.of());

    private McpConfirmations() {
    }

    public static ElicitResult confirm(McpSyncRequestContext context, String message, Map<String, Object> meta) {
        return context.elicit(
                ElicitRequest.builder()
                        .message(message)
                        .requestedSchema(CONFIRMATION_SCHEMA)
                        .meta(meta)
                        .build()
        );
    }

    public static ElicitResult confirm(McpSyncRequestContext context, String message) {
        return context.elicit(
                ElicitRequest.builder()
                        .message(message)
                        .requestedSchema(CONFIRMATION_SCHEMA)
                        .build()
        );
    }
}
