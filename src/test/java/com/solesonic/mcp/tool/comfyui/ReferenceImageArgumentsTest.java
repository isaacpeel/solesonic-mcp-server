package com.solesonic.mcp.tool.comfyui;

import com.solesonic.mcp.exception.comfyui.InvalidReferenceImageException;
import com.solesonic.model.comfyui.ReferenceImage;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceImageArgumentsTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    private static final byte[] WEBP_BYTES = {'R', 'I', 'F', 'F', 0x10, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P'};
    private static final byte[] GIF_BYTES = {'G', 'I', 'F', '8', '9', 'a', 0x01, 0x00};

    private final ReferenceImageArguments referenceImageArguments = new ReferenceImageArguments(DataSize.ofKilobytes(1));

    @Test
    void parse_absentArgumentMeansNoReferenceImages() {
        assertThat(referenceImageArguments.parse(null)).isEmpty();
    }

    @Test
    void parse_decodesEachImageInOrder() {
        List<ReferenceImage> referenceImages = referenceImageArguments.parse(List.of(
                image(PNG_BYTES, "image/png"),
                image(JPEG_BYTES, "image/jpeg"),
                image(WEBP_BYTES, "image/webp")));

        assertThat(referenceImages).extracting(ReferenceImage::mimeType)
                .containsExactly("image/png", "image/jpeg", "image/webp");
        assertThat(referenceImages.getFirst().bytes()).isEqualTo(PNG_BYTES);
    }

    @Test
    void parse_toleratesADataUriPrefixAndAnUppercaseMimeType() {
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_BYTES);

        List<ReferenceImage> referenceImages = referenceImageArguments.parse(
                List.of(Map.of("data", dataUri, "mimeType", "IMAGE/PNG")));

        assertThat(referenceImages).singleElement().satisfies(referenceImage -> {
            assertThat(referenceImage.bytes()).isEqualTo(PNG_BYTES);
            assertThat(referenceImage.mimeType()).isEqualTo("image/png");
        });
    }

    /**
     * The declared type is the caller's claim; the bytes are the evidence.
     */
    @Test
    void parse_rejectsContentThatDoesNotMatchTheDeclaredType() {
        assertThatThrownBy(() -> referenceImageArguments.parse(List.of(image(PNG_BYTES, "image/jpeg"))))
                .isInstanceOf(InvalidReferenceImageException.class)
                .hasMessageContaining("Reference image 1")
                .hasMessageContaining("does not match");
    }

    @Test
    void parse_rejectsAnUnsupportedType() {
        assertThatThrownBy(() -> referenceImageArguments.parse(List.of(image(GIF_BYTES, "image/gif"))))
                .isInstanceOf(InvalidReferenceImageException.class)
                .hasMessageContaining("unsupported type");
    }

    @Test
    void parse_rejectsInvalidBase64() {
        assertThatThrownBy(() -> referenceImageArguments.parse(
                List.of(Map.of("data", "not*base64!", "mimeType", "image/png"))))
                .isInstanceOf(InvalidReferenceImageException.class)
                .hasMessageContaining("not valid base64");
    }

    @Test
    void parse_rejectsAnImageLargerThanTheLimit() {
        byte[] oversized = Arrays.copyOf(PNG_BYTES, 2048);

        assertThatThrownBy(() -> referenceImageArguments.parse(List.of(image(oversized, "image/png"))))
                .isInstanceOf(InvalidReferenceImageException.class)
                .hasMessageContaining("larger than");
    }

    /**
     * The encoded-size check runs after whitespace is stripped, so padding must be bounded before
     * that copy is made.
     */
    @Test
    void parse_rejectsAPayloadPaddedWithWhitespace() {
        String padded = Base64.getEncoder().encodeToString(PNG_BYTES) + " ".repeat(100_000);

        assertThatThrownBy(() -> referenceImageArguments.parse(List.of(Map.of("data", padded, "mimeType", "image/png"))))
                .isInstanceOf(InvalidReferenceImageException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    void parse_rejectsMoreThanThreeImages() {
        Map<String, Object> png = image(PNG_BYTES, "image/png");

        assertThatThrownBy(() -> referenceImageArguments.parse(List.of(png, png, png, png)))
                .isInstanceOf(InvalidReferenceImageException.class)
                .hasMessageContaining("At most 3");
    }

    @Test
    void parse_rejectsAnArgumentThatIsNotAnArrayOfObjects() {
        assertThatThrownBy(() -> referenceImageArguments.parse("a string"))
                .isInstanceOf(InvalidReferenceImageException.class)
                .hasMessageContaining("array");

        assertThatThrownBy(() -> referenceImageArguments.parse(List.of("a string")))
                .isInstanceOf(InvalidReferenceImageException.class)
                .hasMessageContaining("Reference image 1");

        assertThatThrownBy(() -> referenceImageArguments.parse(List.of(Map.of("mimeType", "image/png"))))
                .isInstanceOf(InvalidReferenceImageException.class)
                .hasMessageContaining("data");
    }

    /**
     * The message goes back to a model, and through it potentially to a log or a chat transcript.
     */
    @Test
    void parse_neverEchoesTheImageDataInAnError() {
        String data = Base64.getEncoder().encodeToString(PNG_BYTES);

        assertThatThrownBy(() -> referenceImageArguments.parse(List.of(Map.of("data", data, "mimeType", "image/jpeg"))))
                .isInstanceOf(InvalidReferenceImageException.class)
                .satisfies(thrown -> assertThat(thrown.getMessage()).doesNotContain(data));
    }

    private Map<String, Object> image(byte[] bytes, String mimeType) {
        return Map.of("data", Base64.getEncoder().encodeToString(bytes), "mimeType", mimeType);
    }
}
