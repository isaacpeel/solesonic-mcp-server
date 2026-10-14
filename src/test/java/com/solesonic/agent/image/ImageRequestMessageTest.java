package com.solesonic.agent.image;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageRequestMessageTest {

    @Test
    void withImageDescriptions_leadsWithThePhotoCountThenEachDescriptionThenTheRequest() {
        String message = ImageRequestMessage.withImageDescriptions("put us in Paris", 2, List.of("two friends", ""));

        assertThat(message).isEqualTo("""
                [2 photos attached]
                Photo 1: two friends
                Photo 2: no description given
                Request: put us in Paris""");
    }

    @Test
    void withoutImageDescriptions_carriesOnlyThePhotoCountAndTheRequest() {
        assertThat(ImageRequestMessage.withoutImageDescriptions("a lighthouse", 0))
                .isEqualTo("[No photo attached]\nRequest: a lighthouse");
    }

    @Test
    void trimDescription_stripsAndCapsTheDescription() {
        assertThat(ImageRequestMessage.trimDescription(null)).isEmpty();
        assertThat(ImageRequestMessage.trimDescription("  a man  ")).isEqualTo("a man");
        assertThat(ImageRequestMessage.trimDescription("x".repeat(2000)))
                .hasSize(ImageRequestMessage.MAX_DESCRIPTION_LENGTH);
    }
}
