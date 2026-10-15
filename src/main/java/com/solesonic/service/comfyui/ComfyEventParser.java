package com.solesonic.service.comfyui;

import com.solesonic.model.comfyui.ComfyEvent;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionCached;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionFailed;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionInterrupted;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionStarted;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionSucceeded;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuted;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuting;
import com.solesonic.model.comfyui.ComfyEvent.NodeProgress;
import com.solesonic.model.comfyui.ComfyEvent.QueueStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns one text frame from ComfyUI's {@code /ws} stream ({@code {"type": ..., "data": {...}}})
 * into a {@link ComfyEvent}. Types this server does not use — {@code progress_state},
 * {@code feature_flags}, custom-node messages — and frames that are not valid JSON come back empty.
 */
public final class ComfyEventParser {

    private static final Logger log = LoggerFactory.getLogger(ComfyEventParser.class);

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_PROMPT_ID = "prompt_id";
    private static final String FIELD_NODE = "node";
    private static final String FIELD_NODE_ID = "node_id";
    private static final String FIELD_NODE_TYPE = "node_type";

    private static final String TYPE_STATUS = "status";
    private static final String TYPE_EXECUTION_START = "execution_start";
    private static final String TYPE_EXECUTION_CACHED = "execution_cached";
    private static final String TYPE_EXECUTING = "executing";
    private static final String TYPE_PROGRESS = "progress";
    private static final String TYPE_EXECUTED = "executed";
    private static final String TYPE_EXECUTION_SUCCESS = "execution_success";
    private static final String TYPE_EXECUTION_ERROR = "execution_error";
    private static final String TYPE_EXECUTION_INTERRUPTED = "execution_interrupted";

    private final JsonMapper jsonMapper;

    public ComfyEventParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Optional<ComfyEvent> parse(String frame) {
        JsonNode message;

        try {
            message = jsonMapper.readTree(frame);
        } catch (JacksonException jacksonException) {
            log.debug("Ignoring a ComfyUI event frame that is not valid JSON", jacksonException);
            return Optional.empty();
        }

        String type = stringOrNull(message.path(FIELD_TYPE));

        if (type == null) {
            return Optional.empty();
        }

        JsonNode data = message.path(FIELD_DATA);
        String promptId = stringOrNull(data.path(FIELD_PROMPT_ID));

        return switch (type) {
            case TYPE_STATUS -> Optional.of(new QueueStatus(
                    data.path("status").path("exec_info").path("queue_remaining").asInt(0)));
            case TYPE_EXECUTION_START -> Optional.of(new ExecutionStarted(promptId));
            case TYPE_EXECUTION_CACHED -> Optional.of(new ExecutionCached(promptId, nodeIds(data.path("nodes"))));
            case TYPE_EXECUTING -> Optional.of(new NodeExecuting(promptId, stringOrNull(data.path(FIELD_NODE))));
            case TYPE_PROGRESS -> Optional.of(new NodeProgress(promptId,
                    stringOrNull(data.path(FIELD_NODE)),
                    data.path("value").asDouble(0),
                    data.path("max").asDouble(0)));
            case TYPE_EXECUTED -> Optional.of(new NodeExecuted(promptId, stringOrNull(data.path(FIELD_NODE))));
            case TYPE_EXECUTION_SUCCESS -> Optional.of(new ExecutionSucceeded(promptId));
            case TYPE_EXECUTION_ERROR -> Optional.of(new ExecutionFailed(promptId,
                    stringOrNull(data.path(FIELD_NODE_ID)),
                    stringOrNull(data.path(FIELD_NODE_TYPE)),
                    stringOrNull(data.path("exception_message"))));
            case TYPE_EXECUTION_INTERRUPTED -> Optional.of(new ExecutionInterrupted(promptId,
                    stringOrNull(data.path(FIELD_NODE_ID)),
                    stringOrNull(data.path(FIELD_NODE_TYPE))));
            default -> Optional.empty();
        };
    }

    private static List<String> nodeIds(JsonNode nodes) {
        List<String> nodeIds = new ArrayList<>();

        for (JsonNode node : nodes.values()) {
            String nodeId = stringOrNull(node);

            if (nodeId != null) {
                nodeIds.add(nodeId);
            }
        }

        return List.copyOf(nodeIds);
    }

    /**
     * Node ids are strings in ComfyUI's API format, but a hand-written workflow can use bare numbers.
     */
    private static String stringOrNull(JsonNode node) {
        if (node.isString()) {
            return node.stringValue();
        }

        if (node.isNumber()) {
            return node.asString();
        }

        return null;
    }
}
