package com.solesonic.mcp.service.comfyui;

import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_HEIGHT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_PROMPT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_SEED;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.TOKEN_WIDTH;
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
     * nodes, so a substitution regression that landed the prompt on the negative node would generate
     * the wrong image successfully, with no error anywhere to notice.
     */
    @Test
    void build_writesPromptToTheTokenisedNode_andLeavesTheUntokenisedNegativeNodeEmpty() {
        ComfyWorkflowTemplate comfyWorkflowTemplate = ComfyWorkflowTemplate.parse(fixture(), jsonMapper);

        ObjectNode workflow = comfyWorkflowTemplate.build(
                new ImageGenerationRequest("a lighthouse in a storm", 1024, 1024, 42L));

        assertThat(text(workflow, POSITIVE_PROMPT_NODE_ID)).isEqualTo("a lighthouse in a storm");
        assertThat(text(workflow, NEGATIVE_PROMPT_NODE_ID)).isEmpty();
    }

    /**
     * Tokens are replaced with correctly typed JSON, never with the token text: ComfyUI rejects a
     * string where it expects a number, so a textual substitution would fail at the origin.
     */
    @Test
    void build_substitutesSeedAndDimensionsAsNumbers() {
        ComfyWorkflowTemplate comfyWorkflowTemplate = ComfyWorkflowTemplate.parse(fixture(), jsonMapper);

        ObjectNode workflow = comfyWorkflowTemplate.build(
                new ImageGenerationRequest("a lighthouse in a storm", 1344, 768, 987654321L));

        ObjectNode samplerInputs = inputs(workflow, SAMPLER_NODE_ID);
        assertThat(samplerInputs.get("seed").isNumber()).isTrue();
        assertThat(samplerInputs.get("seed").asLong()).isEqualTo(987654321L);

        ObjectNode latentInputs = inputs(workflow, LATENT_NODE_ID);
        assertThat(latentInputs.get("width").isNumber()).isTrue();
        assertThat(latentInputs.get("width").asInt()).isEqualTo(1344);
        assertThat(latentInputs.get("height").asInt()).isEqualTo(768);
    }

    /**
     * Untokenised values are what make a row a preset, so they must survive a build untouched.
     */
    @Test
    void build_leavesUntokenisedGenerationParametersExactlyAsStored() {
        ComfyWorkflowTemplate comfyWorkflowTemplate = ComfyWorkflowTemplate.parse(fixture(), jsonMapper);

        ObjectNode workflow = comfyWorkflowTemplate.build(new ImageGenerationRequest("anything", 512, 512, 1L));

        ObjectNode samplerInputs = inputs(workflow, SAMPLER_NODE_ID);
        assertThat(samplerInputs.get("steps").asInt()).isEqualTo(4);
        assertThat(samplerInputs.get("cfg").asInt()).isEqualTo(1);
        assertThat(samplerInputs.get("sampler_name").asString()).isEqualTo("euler");
        assertThat(inputs(workflow, LATENT_NODE_ID).get("batch_size").asInt()).isEqualTo(1);
        assertThat(inputs(workflow, "30").get("ckpt_name").asString()).isEqualTo("flux1-schnell-fp8.safetensors");
    }

    /**
     * Graph edges are arrays of node id and output index. A walk that rewrote them would silently
     * rewire the graph, so the untouched edge is worth asserting.
     */
    @Test
    void build_leavesGraphEdgesIntact() {
        ComfyWorkflowTemplate comfyWorkflowTemplate = ComfyWorkflowTemplate.parse(fixture(), jsonMapper);

        ObjectNode workflow = comfyWorkflowTemplate.build(new ImageGenerationRequest("anything", 512, 512, 1L));

        assertThat(inputs(workflow, SAMPLER_NODE_ID).get("positive").get(0).asString()).isEqualTo("6");
        assertThat(inputs(workflow, SAMPLER_NODE_ID).get("latent_image").get(0).asString()).isEqualTo("27");
    }

    @Test
    void build_doesNotMutateTheCachedTemplateAcrossCalls() {
        ComfyWorkflowTemplate comfyWorkflowTemplate = ComfyWorkflowTemplate.parse(fixture(), jsonMapper);

        ObjectNode first = comfyWorkflowTemplate.build(new ImageGenerationRequest("first prompt", 1024, 1024, 1L));
        ObjectNode second = comfyWorkflowTemplate.build(new ImageGenerationRequest("second prompt", 832, 1216, 2L));

        assertThat(text(first, POSITIVE_PROMPT_NODE_ID)).isEqualTo("first prompt");
        assertThat(text(second, POSITIVE_PROMPT_NODE_ID)).isEqualTo("second prompt");
        assertThat(inputs(first, LATENT_NODE_ID).get("width").asInt()).isEqualTo(1024);
        assertThat(inputs(second, LATENT_NODE_ID).get("width").asInt()).isEqualTo(832);
        assertThat(inputs(first, SAMPLER_NODE_ID).get("seed").asLong()).isEqualTo(1L);
    }

    @Test
    void parse_recordsWhichTokensTheDocumentCarries() {
        ComfyWorkflowTemplate comfyWorkflowTemplate = ComfyWorkflowTemplate.parse(fixture(), jsonMapper);

        assertThat(comfyWorkflowTemplate.hasToken(TOKEN_PROMPT)).isTrue();
        assertThat(comfyWorkflowTemplate.hasToken(TOKEN_SEED)).isTrue();
        assertThat(comfyWorkflowTemplate.hasToken(TOKEN_WIDTH)).isTrue();
        assertThat(comfyWorkflowTemplate.hasToken(TOKEN_HEIGHT)).isTrue();
    }

    /**
     * A workflow may legitimately omit optional tokens — omitting {@code __SEED__} is how an author
     * asks for a deterministic result. The stored literal must then survive untouched.
     */
    @Test
    void build_leavesOptionalTokensAbsentFromTheDocumentAtTheirStoredValues() {
        String workflowJson = mutatedFixture(workflow -> {
            inputs(workflow, SAMPLER_NODE_ID).put("seed", 999L);
            inputs(workflow, LATENT_NODE_ID).put("width", 640);
        });

        ComfyWorkflowTemplate comfyWorkflowTemplate = ComfyWorkflowTemplate.parse(workflowJson, jsonMapper);

        assertThat(comfyWorkflowTemplate.hasToken(TOKEN_SEED)).isFalse();
        assertThat(comfyWorkflowTemplate.hasToken(TOKEN_WIDTH)).isFalse();
        assertThat(comfyWorkflowTemplate.hasToken(TOKEN_HEIGHT)).isTrue();

        ObjectNode workflow = comfyWorkflowTemplate.build(new ImageGenerationRequest("a prompt", 1344, 768, 42L));

        assertThat(inputs(workflow, SAMPLER_NODE_ID).get("seed").asLong()).isEqualTo(999L);
        assertThat(inputs(workflow, LATENT_NODE_ID).get("width").asInt()).isEqualTo(640);
        assertThat(inputs(workflow, LATENT_NODE_ID).get("height").asInt()).isEqualTo(768);
    }

    /**
     * A row with no prompt token cannot accept input at all, so registering a tool for it would
     * advertise something that silently ignores its only required parameter.
     */
    @Test
    void parse_failsWhenThePromptTokenIsMissing() {
        String workflowJson = mutatedFixture(workflow ->
                inputs(workflow, POSITIVE_PROMPT_NODE_ID).put("text", "a hardcoded prompt"));

        assertThatThrownBy(() -> ComfyWorkflowTemplate.parse(workflowJson, jsonMapper))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining(TOKEN_PROMPT);
    }

    @Test
    void parse_failsOnMalformedJson() {
        assertThatThrownBy(() -> ComfyWorkflowTemplate.parse("{\"6\": ", jsonMapper))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void parse_failsWhenTheDocumentIsNotAJsonObject() {
        assertThatThrownBy(() -> ComfyWorkflowTemplate.parse("[\"__PROMPT__\"]", jsonMapper))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining("not a JSON object");
    }

    /**
     * Tokens bind by value, not by node id or {@code _meta.title}, so a re-export that renumbered
     * every node must still work. This is the failure the previous title-based binding was built to
     * survive, and it is now structural rather than validated.
     */
    @Test
    void build_isUnaffectedByNodeIdsAndTitles() {
        String workflowJson = """
                {
                  "aardvark": {
                    "inputs": {"text": "__PROMPT__"},
                    "class_type": "CLIPTextEncode",
                    "_meta": {"title": "Renamed By A Careless Re-export"}
                  },
                  "banana": {
                    "inputs": {"seed": "__SEED__", "steps": 20}
                  }
                }""";

        ComfyWorkflowTemplate comfyWorkflowTemplate = ComfyWorkflowTemplate.parse(workflowJson, jsonMapper);

        ObjectNode workflow = comfyWorkflowTemplate.build(new ImageGenerationRequest("a prompt", 1024, 1024, 7L));

        assertThat(inputs(workflow, "aardvark").get("text").asString()).isEqualTo("a prompt");
        assertThat(inputs(workflow, "banana").get("seed").asLong()).isEqualTo(7L);
    }

    private String mutatedFixture(Consumer<ObjectNode> mutation) {
        ObjectNode workflow = (ObjectNode) jsonMapper.readTree(fixture());
        mutation.accept(workflow);

        return jsonMapper.writeValueAsString(workflow);
    }

    private String fixture() {
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
