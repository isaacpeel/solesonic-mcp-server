package com.solesonic.mcp.config.comfyui;

public final class ComfyUiConstants {

    private ComfyUiConstants() {}

    public static final String COMFY_UI_WEB_CLIENT = "comfyUiWebClient";

    public static final String PROMPT_ENDPOINT = "/prompt";
    public static final String HISTORY_ENDPOINT = "/history/{promptId}";
    public static final String VIEW_ENDPOINT = "/view";

    public static final String QUERY_PARAM_FILENAME = "filename";
    public static final String QUERY_PARAM_SUBFOLDER = "subfolder";
    public static final String QUERY_PARAM_TYPE = "type";

    public static final String MIME_TYPE_PNG = "image/png";
    public static final String OUTPUT_TYPE = "output";

    /**
     * Substitution tokens. A stored workflow carries these as literal string values wherever a
     * caller-supplied value belongs; {@code ComfyWorkflowTemplate} replaces them with correctly
     * typed JSON before submission, so ComfyUI never sees a token.
     *
     * <p>Binding by token rather than by node id or {@code _meta.title} is what lets an arbitrary
     * API-format export work unmodified, and removes the failure mode where a re-export shifted ids
     * and landed the prompt on the negative node — a wrong image generated with no error anywhere.
     */
    public static final String TOKEN_PROMPT = "__PROMPT__";
    public static final String TOKEN_SEED = "__SEED__";
    public static final String TOKEN_WIDTH = "__WIDTH__";
    public static final String TOKEN_HEIGHT = "__HEIGHT__";

    public static final String STATUS_ERROR = "error";
}
