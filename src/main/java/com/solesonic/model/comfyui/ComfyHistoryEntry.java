package com.solesonic.model.comfyui;

import java.util.Map;

/**
 * One entry from {@code GET /history/{promptId}}, keyed in the response by prompt id.
 * While the job is pending or running the whole response is {@code {}}.
 */
public record ComfyHistoryEntry(
        Map<String, ComfyNodeOutput> outputs,
        ComfyExecutionStatus status
) {}
