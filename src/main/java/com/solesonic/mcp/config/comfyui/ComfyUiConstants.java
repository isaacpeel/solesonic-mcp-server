package com.solesonic.mcp.config.comfyui;

public final class ComfyUiConstants {

    private ComfyUiConstants() {}

    public static final String COMFY_UI_WEB_CLIENT = "comfyUiWebClient";

    public static final String PROMPT_ENDPOINT = "/prompt";
    public static final String HISTORY_ENDPOINT = "/history/{promptId}";
    public static final String VIEW_ENDPOINT = "/view";
    public static final String FREE_ENDPOINT = "/free";
    public static final String UPLOAD_ENDPOINT = "/upload/image";
    public static final String WEBSOCKET_ENDPOINT = "/ws";

    public static final String QUERY_PARAM_CLIENT_ID = "clientId";
    public static final String QUERY_PARAM_FILENAME = "filename";
    public static final String QUERY_PARAM_SUBFOLDER = "subfolder";
    public static final String QUERY_PARAM_TYPE = "type";

    public static final String UPLOAD_FIELD_IMAGE = "image";
    public static final String UPLOAD_FIELD_TYPE = "type";
    public static final String UPLOAD_FIELD_OVERWRITE = "overwrite";
    public static final String UPLOAD_FIELD_SUBFOLDER = "subfolder";

    /**
     * Every upload lands in this one subfolder of ComfyUI's {@code input/} directory, because
     * ComfyUI has no API to delete an input and an operator pruning by hand needs one place to look.
     */
    public static final String UPLOAD_SUBFOLDER = "mcp-server";
    public static final String UPLOAD_FILENAME_PREFIX = "mcp-";

    public static final String MIME_TYPE_PNG = "image/png";
    public static final String MIME_TYPE_JPEG = "image/jpeg";
    public static final String MIME_TYPE_WEBP = "image/webp";
    public static final String OUTPUT_TYPE = "output";
    public static final String INPUT_TYPE = "input";

    /**
     * The most reference images any workflow may take. A fixed graph has exactly one
     * {@code LoadImage} node per slot, so this also bounds the slots a variant may declare.
     */
    public static final int MAX_REFERENCE_IMAGES = 3;

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

    /**
     * {@code __REFERENCE_IMAGE_1__} through {@code __REFERENCE_IMAGE_3__}: a {@code LoadImage}
     * node's {@code image} input in a reference variant, replaced with the uploaded file's name.
     */
    public static final String TOKEN_REFERENCE_IMAGE_PREFIX = "__REFERENCE_IMAGE_";
    public static final String TOKEN_SUFFIX = "__";

    public static final String STATUS_ERROR = "error";
}
