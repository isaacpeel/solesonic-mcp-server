package com.solesonic.mcp.model.comfyui;

import java.util.Map;

public class ComfyNode {
    private Map<String, Object> inputs;
    private String class_type;
    private ComfyMeta _meta;

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    public String getClass_type() {
        return class_type;
    }

    public void setClass_type(String class_type) {
        this.class_type = class_type;
    }

    public ComfyMeta get_meta() {
        return _meta;
    }

    public void set_meta(ComfyMeta _meta) {
        this._meta = _meta;
    }
}
