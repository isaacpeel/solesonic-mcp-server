package com.solesonic.mcp.model.comfyui;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ComfyWorkflowResponse {
    @JsonProperty("prompt_id")
    private String promptId;

    private int number;

    @JsonProperty("node_errors")
    private Object nodeErrors;

    public String getPromptId() {
        return promptId;
    }

    public void setPromptId(String promptId) {
        this.promptId = promptId;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public Object getNodeErrors() {
        return nodeErrors;
    }

    public void setNodeErrors(Object nodeErrors) {
        this.nodeErrors = nodeErrors;
    }
}
