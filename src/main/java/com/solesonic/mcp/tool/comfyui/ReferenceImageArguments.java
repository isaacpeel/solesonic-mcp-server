package com.solesonic.mcp.tool.comfyui;

import com.solesonic.mcp.exception.comfyui.InvalidReferenceImageException;
import com.solesonic.model.comfyui.ReferenceImage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MAX_REFERENCE_IMAGES;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MIME_TYPE_JPEG;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MIME_TYPE_PNG;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MIME_TYPE_WEBP;

/**
 * Turns the raw {@code referenceImages} tool argument into decoded, verified images.
 *
 * <p>The declared {@code mimeType} is only a claim: the decoded bytes must carry that format's
 * magic number, so a file that is not what it says never reaches ComfyUI's image loader. Sizes are
 * checked against the encoded length before decoding, so an oversized payload is refused without
 * allocating it twice.
 *
 * <p>Error messages go back to the caller as a tool result and therefore never include the data.
 */
@Component
public class ReferenceImageArguments {

    public static final String PARAM_REFERENCE_IMAGES = "referenceImages";
    public static final String FIELD_DATA = "data";
    public static final String FIELD_MIME_TYPE = "mimeType";

    public static final Set<String> SUPPORTED_MIME_TYPES = Set.of(MIME_TYPE_PNG, MIME_TYPE_JPEG, MIME_TYPE_WEBP);

    private static final String DATA_URI_PREFIX = "data:";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_SIGNATURE = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_SIGNATURE = {'W', 'E', 'B', 'P'};
    private static final int WEBP_SIGNATURE_OFFSET = 8;

    /**
     * Headroom over the exact encoded size for a data URI prefix and line-wrapping whitespace.
     */
    private static final long RAW_LENGTH_SLACK = 1024;

    private final long maxBytes;

    /**
     * Bounds the raw string before any copy of it is made, so whitespace padding cannot slip a huge
     * payload past the encoded-size check, which only runs once whitespace is gone.
     */
    private final long maxRawLength;

    public ReferenceImageArguments(@Value("${comfyui.reference-image.max-bytes}") DataSize maxBytes) {
        this.maxBytes = maxBytes.toBytes();
        this.maxRawLength = (this.maxBytes + 2) / 3 * 4 * 2 + RAW_LENGTH_SLACK;
    }

    /**
     * @param argument the {@code referenceImages} value exactly as it arrived, or null when absent
     * @return the images in caller order; empty when none were supplied
     * @throws InvalidReferenceImageException when any image is unusable
     */
    public List<ReferenceImage> parse(Object argument) {
        if (argument == null) {
            return List.of();
        }

        if (!(argument instanceof List<?> entries)) {
            throw new InvalidReferenceImageException(
                    PARAM_REFERENCE_IMAGES + " must be an array of {data, mimeType} objects.");
        }

        if (entries.size() > MAX_REFERENCE_IMAGES) {
            throw new InvalidReferenceImageException("At most %d reference images are accepted; %d supplied."
                    .formatted(MAX_REFERENCE_IMAGES, entries.size()));
        }

        List<ReferenceImage> referenceImages = new ArrayList<>(entries.size());

        for (int index = 0; index < entries.size(); index++) {
            referenceImages.add(referenceImage(index + 1, entries.get(index)));
        }

        return List.copyOf(referenceImages);
    }

    private ReferenceImage referenceImage(int position, Object entry) {
        if (!(entry instanceof Map<?, ?> fields)) {
            throw invalid(position, "must be an object with data and mimeType.");
        }

        if (!(fields.get(FIELD_DATA) instanceof String data) || StringUtils.isBlank(data)) {
            throw invalid(position, "has no data.");
        }

        if (!(fields.get(FIELD_MIME_TYPE) instanceof String declaredMimeType)) {
            throw invalid(position, "has no mimeType.");
        }

        String mimeType = declaredMimeType.trim().toLowerCase(Locale.ROOT);

        if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw invalid(position, "has an unsupported type; use image/png, image/jpeg or image/webp.");
        }

        byte[] bytes = decode(position, data);

        if (!mimeType.equals(detectedMimeType(bytes))) {
            throw invalid(position, "content does not match its declared type " + mimeType + ".");
        }

        return new ReferenceImage(bytes, mimeType);
    }

    private byte[] decode(int position, String data) {
        if (data.length() > maxRawLength) {
            throw tooLarge(position);
        }

        String encoded = StringUtils.deleteWhitespace(stripDataUriPrefix(data));

        //Three bytes per four characters: an upper bound on the decoded size, checked before decoding.
        if ((long) encoded.length() / 4 * 3 > maxBytes + 2) {
            throw tooLarge(position);
        }

        byte[] bytes;

        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw invalid(position, "is not valid base64.");
        }

        if (bytes.length == 0) {
            throw invalid(position, "is empty.");
        }

        if (bytes.length > maxBytes) {
            throw tooLarge(position);
        }

        return bytes;
    }

    private static String stripDataUriPrefix(String data) {
        if (!data.startsWith(DATA_URI_PREFIX)) {
            return data;
        }

        int comma = data.indexOf(',');

        return comma < 0 ? data : data.substring(comma + 1);
    }

    private static String detectedMimeType(byte[] bytes) {
        if (startsWith(bytes, PNG_SIGNATURE, 0)) {
            return MIME_TYPE_PNG;
        }

        if (startsWith(bytes, JPEG_SIGNATURE, 0)) {
            return MIME_TYPE_JPEG;
        }

        if (startsWith(bytes, RIFF_SIGNATURE, 0) && startsWith(bytes, WEBP_SIGNATURE, WEBP_SIGNATURE_OFFSET)) {
            return MIME_TYPE_WEBP;
        }

        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] signature, int offset) {
        if (bytes.length < offset + signature.length) {
            return false;
        }

        for (int index = 0; index < signature.length; index++) {
            if (bytes[offset + index] != signature[index]) {
                return false;
            }
        }

        return true;
    }

    private InvalidReferenceImageException tooLarge(int position) {
        return invalid(position, "is larger than the %s limit.".formatted(DataSize.ofBytes(maxBytes)));
    }

    private static InvalidReferenceImageException invalid(int position, String problem) {
        return new InvalidReferenceImageException("Reference image " + position + " " + problem);
    }
}
