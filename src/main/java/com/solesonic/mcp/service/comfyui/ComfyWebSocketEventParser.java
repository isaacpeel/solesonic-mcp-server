package com.solesonic.mcp.service.comfyui;

import tools.jackson.databind.JsonNode;
import com.solesonic.mcp.model.comfyui.websocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses JSON messages from ComfyUI WebSocket into typed event objects.
 */
@Component
public class ComfyWebSocketEventParser {

    private static final Logger log = LoggerFactory.getLogger(ComfyWebSocketEventParser.class);

    private static final String TYPE_FIELD = "type";
    private static final String DATA_FIELD = "data";
    private static final String PROMPT_ID_FIELD = "prompt_id";
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String NODE_FIELD = "node";
    private static final String NODES_FIELD = "nodes";
    private static final String VALUE_FIELD = "value";
    private static final String MAX_FIELD = "max";
    private static final String STATUS_FIELD = "status";
    private static final String QUEUE_REMAINING_FIELD = "exec_info";
    private static final String EXCEPTION_MESSAGE_FIELD = "exception_message";
    private static final String EXCEPTION_TYPE_FIELD = "exception_type";

    private final JsonMapper jsonMapper;

    public ComfyWebSocketEventParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Parses a JSON string message into a ComfyWebSocketEvent.
     */
    public ComfyWebSocketEvent parse(String jsonMessage) {
        try {
            JsonNode rootNode = jsonMapper.readTree(jsonMessage);
            return parseNode(rootNode);
        } catch (Exception exception) {
            log.warn("Failed to parse WebSocket message: {}", jsonMessage, exception);
            return new ComfyUnknownEvent("parse_error", null, null);
        }
    }

    private ComfyWebSocketEvent parseNode(JsonNode rootNode) {
        String eventType = getTextValue(rootNode, TYPE_FIELD);
        JsonNode dataNode = rootNode.get(DATA_FIELD);

        if (eventType == null) {
            log.warn("WebSocket message missing 'type' field: {}", rootNode);
            return new ComfyUnknownEvent("missing_type", null, rootNode);
        }

        return switch (eventType) {
            case ComfyStatusEvent.EVENT_TYPE -> parseStatusEvent(dataNode, rootNode);
            case ComfyExecutionStartEvent.EVENT_TYPE -> parseExecutionStartEvent(dataNode, rootNode);
            case ComfyExecutionCachedEvent.EVENT_TYPE -> parseExecutionCachedEvent(dataNode, rootNode);
            case ComfyExecutingEvent.EVENT_TYPE -> parseExecutingEvent(dataNode, rootNode);
            case ComfyProgressEvent.EVENT_TYPE -> parseProgressEvent(dataNode, rootNode);
            case ComfyExecutionSuccessEvent.EVENT_TYPE -> parseExecutionSuccessEvent(dataNode, rootNode);
            case ComfyExecutionErrorEvent.EVENT_TYPE -> parseExecutionErrorEvent(dataNode, rootNode);
            default -> {
                log.warn("Unknown WebSocket event type '{}': {}", eventType, rootNode);
                yield new ComfyUnknownEvent(eventType, getPromptId(dataNode), rootNode);
            }
        };
    }

    private ComfyStatusEvent parseStatusEvent(JsonNode dataNode, JsonNode rootNode) {
        int queueRemaining = 0;

        if (dataNode != null && dataNode.has(STATUS_FIELD)) {
            JsonNode statusNode = dataNode.get(STATUS_FIELD);

            if (statusNode.has(QUEUE_REMAINING_FIELD)) {
                JsonNode execInfo = statusNode.get(QUEUE_REMAINING_FIELD);

                if (execInfo.has("queue_remaining")) {
                    queueRemaining = execInfo.get("queue_remaining").asInt(0);
                }
            }
        }

        return new ComfyStatusEvent(
                getPromptId(dataNode),
                queueRemaining,
                rootNode
        );
    }

    private ComfyExecutionStartEvent parseExecutionStartEvent(JsonNode dataNode, JsonNode rootNode) {
        return new ComfyExecutionStartEvent(
                getPromptId(dataNode),
                getLongValue(dataNode),
                rootNode
        );
    }

    private ComfyExecutionCachedEvent parseExecutionCachedEvent(JsonNode dataNode, JsonNode rootNode) {
        List<String> cachedNodes = new ArrayList<>();

        if (dataNode != null && dataNode.has(NODES_FIELD)) {
            JsonNode nodesArray = dataNode.get(NODES_FIELD);

            if (nodesArray.isArray()) {
                for (JsonNode nodeElement : nodesArray) {
                    cachedNodes.add(nodeElement.asString());
                }
            }
        }

        return new ComfyExecutionCachedEvent(
                getPromptId(dataNode),
                cachedNodes,
                getLongValue(dataNode),
                rootNode
        );
    }

    private ComfyExecutingEvent parseExecutingEvent(JsonNode dataNode, JsonNode rootNode) {
        return new ComfyExecutingEvent(
                getPromptId(dataNode),
                getTextValue(dataNode, NODE_FIELD),
                rootNode
        );
    }

    private ComfyProgressEvent parseProgressEvent(JsonNode dataNode, JsonNode rootNode) {
        return new ComfyProgressEvent(
                getPromptId(dataNode),
                getTextValue(dataNode, NODE_FIELD),
                getIntValue(dataNode, VALUE_FIELD),
                getIntValue(dataNode, MAX_FIELD),
                rootNode
        );
    }

    private ComfyExecutionSuccessEvent parseExecutionSuccessEvent(JsonNode dataNode, JsonNode rootNode) {
        return new ComfyExecutionSuccessEvent(
                getPromptId(dataNode),
                getLongValue(dataNode),
                rootNode
        );
    }

    private ComfyExecutionErrorEvent parseExecutionErrorEvent(JsonNode dataNode, JsonNode rootNode) {
        String exceptionMessage = getTextValue(dataNode, EXCEPTION_MESSAGE_FIELD);
        String exceptionType = getTextValue(dataNode, EXCEPTION_TYPE_FIELD);

        return new ComfyExecutionErrorEvent(
                getPromptId(dataNode),
                exceptionMessage != null ? exceptionMessage : "Unknown error",
                exceptionType,
                exceptionMessage,
                rootNode
        );
    }

    private String getPromptId(JsonNode dataNode) {
        return getTextValue(dataNode, PROMPT_ID_FIELD);
    }

    private String getTextValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }

        JsonNode fieldNode = node.get(fieldName);

        if (fieldNode.isNull()) {
            return null;
        }

        return fieldNode.asString();
    }

    private int getIntValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return 0;
        }

        return node.get(fieldName).asInt(0);
    }

    private long getLongValue(JsonNode node) {
        if (node == null || !node.has(ComfyWebSocketEventParser.TIMESTAMP_FIELD)) {
            return 0L;
        }

        return node.get(ComfyWebSocketEventParser.TIMESTAMP_FIELD).asLong(0L);
    }
}
