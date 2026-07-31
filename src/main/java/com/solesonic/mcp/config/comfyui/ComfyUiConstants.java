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

    public static final String NODE_TITLE_POSITIVE_PROMPT = "CLIP Text Encode (Positive Prompt)";
    public static final String NODE_TITLE_SAMPLER = "KSampler";
    public static final String NODE_TITLE_LATENT = "EmptySD3LatentImage";

    public static final String CLASS_TYPE_CLIP_TEXT_ENCODE = "CLIPTextEncode";
    public static final String CLASS_TYPE_K_SAMPLER = "KSampler";
    public static final String CLASS_TYPE_EMPTY_SD3_LATENT = "EmptySD3LatentImage";

    public static final String FIELD_CLASS_TYPE = "class_type";
    public static final String FIELD_META = "_meta";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_INPUTS = "inputs";

    public static final String INPUT_TEXT = "text";
    public static final String INPUT_SEED = "seed";
    public static final String INPUT_NOISE_SEED = "noise_seed";
    public static final String INPUT_STEPS = "steps";
    public static final String INPUT_WIDTH = "width";
    public static final String INPUT_HEIGHT = "height";

    public static final String STATUS_ERROR = "error";

    /**
     * Fixed generation parameters. The tool exposes only a prompt, so these are not caller-tunable:
     * FLUX.1-schnell is distilled for 4 steps, and 1024x1024 is its native resolution.
     */
    public static final int DEFAULT_WIDTH = 1024;
    public static final int DEFAULT_HEIGHT = 1024;
    public static final int DEFAULT_STEPS = 4;
}
