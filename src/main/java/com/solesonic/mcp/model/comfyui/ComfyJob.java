package com.solesonic.mcp.model.comfyui;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ComfyJob {
    private String id;
    private String status;

    @JsonProperty("create_time")
    private long createTime;

    @JsonProperty("execution_start_time")
    private long executionStartTime;

    @JsonProperty("execution_end_time")
    private long executionEndTime;

    @JsonProperty("outputs_count")
    private int outputsCount;

    @JsonProperty("preview_output")
    private ComfyPreviewOutput previewOutput;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getExecutionStartTime() {
        return executionStartTime;
    }

    public void setExecutionStartTime(long executionStartTime) {
        this.executionStartTime = executionStartTime;
    }

    public long getExecutionEndTime() {
        return executionEndTime;
    }

    public void setExecutionEndTime(long executionEndTime) {
        this.executionEndTime = executionEndTime;
    }

    public int getOutputsCount() {
        return outputsCount;
    }

    public void setOutputsCount(int outputsCount) {
        this.outputsCount = outputsCount;
    }

    public ComfyPreviewOutput getPreviewOutput() {
        return previewOutput;
    }

    public void setPreviewOutput(ComfyPreviewOutput previewOutput) {
        this.previewOutput = previewOutput;
    }
}
