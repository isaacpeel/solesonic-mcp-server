package com.solesonic.mcp.model.comfyui;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComfyWorkflow {

    private ComfyPrompt prompt;

    @JsonProperty("client_id")
    private String clientId;

    public ComfyPrompt getPrompt() {
        return prompt;
    }

    public void setPrompt(ComfyPrompt prompt) {
        this.prompt = prompt;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
