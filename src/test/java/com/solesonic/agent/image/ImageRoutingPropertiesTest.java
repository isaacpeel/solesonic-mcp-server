package com.solesonic.agent.image;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImageRoutingPropertiesTest {

    /**
     * Kebab-case intent keys, as written in application.properties, bind to the enum constants.
     */
    @Test
    void bind_kebabCaseIntentKeysAndCommaSeparatedWorkflows() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "solesonic.image.default-workflow", "generate_image",
                "solesonic.image.intent-workflows.new-image", "generate_image,generate_special_image",
                "solesonic.image.intent-workflows.edit-person-in-photo", "person_reference_image"));

        ImageRoutingProperties imageRoutingProperties = new Binder(source)
                .bind("solesonic.image", ImageRoutingProperties.class)
                .get();

        assertThat(imageRoutingProperties.defaultWorkflow()).isEqualTo("generate_image");
        assertThat(imageRoutingProperties.workflowsFor(ImageIntent.NEW_IMAGE))
                .containsExactly("generate_image", "generate_special_image");
        assertThat(imageRoutingProperties.workflowsFor(ImageIntent.EDIT_PERSON_IN_PHOTO))
                .containsExactly("person_reference_image");
        assertThat(imageRoutingProperties.workflowsFor(ImageIntent.RESTYLE_PHOTO)).isEmpty();
    }

    @Test
    void workflowsFor_anUndecidedIntentHasNoWorkflows() {
        ImageRoutingProperties imageRoutingProperties = new ImageRoutingProperties("generate_image", null);

        assertThat(imageRoutingProperties.workflowsFor(null)).isEqualTo(List.of());
    }
}
