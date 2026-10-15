package com.solesonic.mcp.service.comfyui;

import com.solesonic.model.comfyui.ComfyEvent.ExecutionCached;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionFailed;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionInterrupted;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionStarted;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionSucceeded;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuted;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuting;
import com.solesonic.model.comfyui.ComfyEvent.NodeProgress;
import com.solesonic.model.comfyui.ComfyEvent.QueueStatus;
import com.solesonic.service.comfyui.ComfyEventParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Frames are shaped as ComfyUI's {@code server.py} and {@code execution.py} send them.
 */
class ComfyEventParserTest {

    private final ComfyEventParser comfyEventParser = new ComfyEventParser(JsonMapper.builder().build());

    @Test
    void parse_status_readsQueueRemaining() {
        String frame = """
                {"type":"status","data":{"status":{"exec_info":{"queue_remaining":3}},"sid":"client-1"}}""";

        assertThat(comfyEventParser.parse(frame)).contains(new QueueStatus(3));
    }

    @Test
    void parse_executionLifecycle() {
        assertThat(comfyEventParser.parse("""
                {"type":"execution_start","data":{"prompt_id":"abc-123","timestamp":1}}"""))
                .contains(new ExecutionStarted("abc-123"));
        assertThat(comfyEventParser.parse("""
                {"type":"execution_cached","data":{"nodes":["4","6"],"prompt_id":"abc-123","timestamp":1}}"""))
                .contains(new ExecutionCached("abc-123", List.of("4", "6")));
        assertThat(comfyEventParser.parse("""
                {"type":"executing","data":{"node":"3","display_node":"3","prompt_id":"abc-123"}}"""))
                .contains(new NodeExecuting("abc-123", "3"));
        assertThat(comfyEventParser.parse("""
                {"type":"progress","data":{"value":12,"max":30,"prompt_id":"abc-123","node":"3"}}"""))
                .contains(new NodeProgress("abc-123", "3", 12, 30));
        assertThat(comfyEventParser.parse("""
                {"type":"executed","data":{"node":"9","display_node":"9","output":{"images":[]},"prompt_id":"abc-123"}}"""))
                .contains(new NodeExecuted("abc-123", "9"));
        assertThat(comfyEventParser.parse("""
                {"type":"execution_success","data":{"prompt_id":"abc-123","timestamp":1}}"""))
                .contains(new ExecutionSucceeded("abc-123"));
    }

    @Test
    void parse_executingWithNullNode_isTheEndOfPromptSignal() {
        assertThat(comfyEventParser.parse("""
                {"type":"executing","data":{"node":null,"prompt_id":"abc-123"}}"""))
                .contains(new NodeExecuting("abc-123", null));
    }

    @Test
    void parse_numericNodeId_isReadAsAString() {
        assertThat(comfyEventParser.parse("""
                {"type":"executing","data":{"node":3,"prompt_id":"abc-123"}}"""))
                .contains(new NodeExecuting("abc-123", "3"));
    }

    @Test
    void parse_executionError_keepsTheNodeAndMessage() {
        String frame = """
                {"type":"execution_error","data":{"prompt_id":"abc-123","node_id":"3","node_type":"KSampler",
                "executed":["4"],"exception_message":"Allocation on device failed","exception_type":"OutOfMemoryError",
                "traceback":["line"],"current_inputs":{},"current_outputs":[]}}""";

        assertThat(comfyEventParser.parse(frame))
                .contains(new ExecutionFailed("abc-123", "3", "KSampler", "Allocation on device failed"));
    }

    @Test
    void parse_executionInterrupted() {
        String frame = """
                {"type":"execution_interrupted","data":{"prompt_id":"abc-123","node_id":"3","node_type":"KSampler","executed":[]}}""";

        assertThat(comfyEventParser.parse(frame)).contains(new ExecutionInterrupted("abc-123", "3", "KSampler"));
    }

    @Test
    void parse_unusedOrMalformedFrames_areEmpty() {
        assertThat(comfyEventParser.parse("""
                {"type":"progress_state","data":{"prompt_id":"abc-123","nodes":{}}}""")).isEmpty();
        assertThat(comfyEventParser.parse("""
                {"type":"feature_flags","data":{}}""")).isEmpty();
        assertThat(comfyEventParser.parse("""
                {"data":{}}""")).isEmpty();
        assertThat(comfyEventParser.parse("not json")).isEmpty();
    }
}
