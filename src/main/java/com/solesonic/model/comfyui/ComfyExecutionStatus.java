package com.solesonic.model.comfyui;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ComfyExecutionStatus(
        @JsonProperty("status_str") String statusStr,
        Boolean completed
) {}
