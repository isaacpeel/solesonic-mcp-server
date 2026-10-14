package com.solesonic.agent.image;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Builds the user turn the routing model reads. The attached-photo facts lead the message rather
 * than living only in the system prompt: a small routing model weights the user turn heavily, and
 * once ignored a system-prompt-only image count.
 *
 * <p>Image descriptions are only ever given to the routing steps (intent and workflow selection),
 * never to the prompt-writing step, so a description cannot leak into the prompt sent to the image
 * model.
 */
public final class ImageRequestMessage {

    public static final int MAX_DESCRIPTION_LENGTH = 500;

    private static final String NO_DESCRIPTION = "no description given";

    private ImageRequestMessage() {
    }

    public static String withImageDescriptions(String userPrompt, int referenceImageCount, List<String> referenceImageDescriptions) {
        StringBuilder message = new StringBuilder(header(referenceImageCount)).append('\n');

        for (int index = 0; index < referenceImageCount; index++) {
            String description = index < referenceImageDescriptions.size() ? referenceImageDescriptions.get(index) : "";

            message.append("Photo ").append(index + 1).append(": ")
                    .append(StringUtils.isBlank(description) ? NO_DESCRIPTION : description)
                    .append('\n');
        }

        return message.append("Request: ").append(userPrompt).toString();
    }

    public static String withoutImageDescriptions(String userPrompt, int referenceImageCount) {
        return header(referenceImageCount) + "\nRequest: " + userPrompt;
    }

    /**
     * Normalises a caller-supplied description: blank for none, and capped so a client cannot push
     * an essay into every routing call or into checkpointed state.
     */
    public static String trimDescription(String description) {
        if (description == null) {
            return "";
        }

        return StringUtils.truncate(description.strip(), MAX_DESCRIPTION_LENGTH);
    }

    private static String header(int referenceImageCount) {
        if (referenceImageCount == 0) {
            return "[No photo attached]";
        }

        return referenceImageCount == 1 ? "[1 photo attached]" : "[" + referenceImageCount + " photos attached]";
    }
}
