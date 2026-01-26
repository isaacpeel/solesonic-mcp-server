package com.solesonic.mcp.model.comfyui;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

public class ComfyPrompt {
    private Map<String, ComfyNode> nodes = new HashMap<>();

    @JsonAnyGetter
    public Map<String, ComfyNode> getNodes() {
        return nodes;
    }

    @JsonAnySetter
    public void addNode(String key, ComfyNode value) {
        nodes.put(key, value);
    }

    public void setNodes(Map<String, ComfyNode> nodes) {
        this.nodes = nodes;
    }
}
