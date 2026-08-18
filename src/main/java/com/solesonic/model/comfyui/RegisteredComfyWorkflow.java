package com.solesonic.model.comfyui;

import com.solesonic.service.comfyui.ComfyWorkflowTemplate;

/**
 * A stored workflow that survived validation and will be exposed as an MCP tool. Pairs the row's
 * identity — the columns a client sees when choosing a tool — with its parsed, ready-to-patch
 * template.
 */
public record RegisteredComfyWorkflow(
        String toolName,
        String name,
        String description,
        ComfyWorkflowTemplate template
) {
}
