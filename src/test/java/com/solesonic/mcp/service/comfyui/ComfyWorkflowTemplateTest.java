package com.solesonic.mcp.service.comfyui;

import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComfyWorkflowTemplateTest {

    private static final String FIXTURE = "comfyui/flux1-schnell-test.json";

    private static final String POSITIVE_PROMPT_NODE_ID = "6";
    private static final String NEGATIVE_PROMPT_NODE_ID = "33";
    private static final String LATENT_NODE_ID = "27";
    private static final String SAMPLER_NODE_ID = "31";

    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
    }

    /**
     * The single most important assertion in this suite. The graph holds two {@code CLIPTextEncode}
     * nodes, so a binding regression that landed the prompt on the negative node would generate the
     * wrong image successfully, with no error anywhere to notice.
     */
    @Test
    void build_writesPromptToPositiveNode_andLeavesNegativeNodeEmpty() {
        ComfyWorkflowTemplate comfyWorkflowTemplate = new ComfyWorkflowTemplate(new ClassPathResource(FIXTURE), jsonMapper);

        ObjectNode workflow = comfyWorkflowTemplate.build(new ImageGenerationRequest("a lighthouse in a storm", 1024, 1024, 4, 42L));

        assertThat(text(workflow, POSITIVE_PROMPT_NODE_ID)).isEqualTo("a lighthouse in a storm");
        assertThat(text(workflow, NEGATIVE_PROMPT_NODE_ID)).isEmpty();
    }

    @Test
    void build_patchesSeedStepsAndDimensionsOnTheCorrectNodes() {
        ComfyWorkflowTemplate comfyWorkflowTemplate = new ComfyWorkflowTemplate(new ClassPathResource(FIXTURE), jsonMapper);

        ObjectNode workflow = comfyWorkflowTemplate.build(new ImageGenerationRequest("a lighthouse in a storm", 1344, 768, 6, 987654321L));

        ObjectNode samplerInputs = inputs(workflow, SAMPLER_NODE_ID);
        assertThat(samplerInputs.get("seed").asLong()).isEqualTo(987654321L);
        assertThat(samplerInputs.get("steps").asInt()).isEqualTo(6);

        ObjectNode latentInputs = inputs(workflow, LATENT_NODE_ID);
        assertThat(latentInputs.get("width").asInt()).isEqualTo(1344);
        assertThat(latentInputs.get("height").asInt()).isEqualTo(768);
    }

    @Test
    void build_doesNotMutateTheCachedTemplateAcrossCalls() {
        ComfyWorkflowTemplate comfyWorkflowTemplate = new ComfyWorkflowTemplate(new ClassPathResource(FIXTURE), jsonMapper);

        ObjectNode first = comfyWorkflowTemplate.build(new ImageGenerationRequest("first prompt", 1024, 1024, 4, 1L));
        ObjectNode second = comfyWorkflowTemplate.build(new ImageGenerationRequest("second prompt", 832, 1216, 8, 2L));

        assertThat(text(first, POSITIVE_PROMPT_NODE_ID)).isEqualTo("first prompt");
        assertThat(text(second, POSITIVE_PROMPT_NODE_ID)).isEqualTo("second prompt");
        assertThat(inputs(first, LATENT_NODE_ID).get("width").asInt()).isEqualTo(1024);
        assertThat(inputs(second, LATENT_NODE_ID).get("width").asInt()).isEqualTo(832);
        assertThat(inputs(first, SAMPLER_NODE_ID).get("seed").asLong()).isEqualTo(1L);
    }

    @Test
    void construction_failsWhenABoundTitleIsMissing() {
        Resource resource = mutatedFixture(workflow ->
                ((ObjectNode) node(workflow, POSITIVE_PROMPT_NODE_ID).get("_meta"))
                        .put("title", "Renamed By A Careless Re-export"));

        assertThatThrownBy(() -> new ComfyWorkflowTemplate(resource, jsonMapper))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining("CLIP Text Encode (Positive Prompt)");
    }

    @Test
    void construction_failsWhenABoundTitleIsOnTheWrongClass() {
        Resource resource = mutatedFixture(workflow ->
                node(workflow, POSITIVE_PROMPT_NODE_ID).put("class_type", "PrimitiveStringMultiline"));

        assertThatThrownBy(() -> new ComfyWorkflowTemplate(resource, jsonMapper))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining("PrimitiveStringMultiline");
    }

    @Test
    void construction_acceptsNoiseSeedInsteadOfSeed() {
        Resource resource = mutatedFixture(workflow -> {
            ObjectNode samplerInputs = inputs(workflow, SAMPLER_NODE_ID);
            long existingSeed = samplerInputs.get("seed").asLong();
            samplerInputs.without("seed").put("noise_seed", existingSeed);
        });

        ComfyWorkflowTemplate comfyWorkflowTemplate = new ComfyWorkflowTemplate(resource, jsonMapper);

        ObjectNode workflow = comfyWorkflowTemplate.build(new ImageGenerationRequest("a lighthouse in a storm", 1024, 1024, 4, 555L));

        assertThat(inputs(workflow, SAMPLER_NODE_ID).get("noise_seed").asLong()).isEqualTo(555L);
        assertThat(inputs(workflow, SAMPLER_NODE_ID).has("seed")).isFalse();
    }

    @Test
    void construction_failsWhenNeitherSeedKeyIsPresent() {
        Resource resource = mutatedFixture(workflow -> inputs(workflow, SAMPLER_NODE_ID).without("seed"));

        assertThatThrownBy(() -> new ComfyWorkflowTemplate(resource, jsonMapper))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining("noise_seed");
    }

    private Resource mutatedFixture(Consumer<ObjectNode> mutation) {
        ObjectNode workflow = (ObjectNode) jsonMapper.readTree(readFixture());
        mutation.accept(workflow);

        return new ByteArrayResource(jsonMapper.writeValueAsString(workflow).getBytes(StandardCharsets.UTF_8));
    }

    private String readFixture() {
        try (InputStream inputStream = new ClassPathResource(FIXTURE).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            throw new IllegalStateException("Unable to read fixture " + FIXTURE, ioException);
        }
    }

    private String text(ObjectNode workflow, String nodeId) {
        return inputs(workflow, nodeId).get("text").asString();
    }

    private ObjectNode inputs(ObjectNode workflow, String nodeId) {
        return (ObjectNode) node(workflow, nodeId).get("inputs");
    }

    private ObjectNode node(ObjectNode workflow, String nodeId) {
        return (ObjectNode) workflow.get(nodeId);
    }
}
