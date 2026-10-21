package com.solesonic.mcp.tool.comfyui;

import com.solesonic.agent.checkpoint.GraphRunner;
import com.solesonic.agent.image.ImageWorkflowSelection;
import com.solesonic.agent.image.ImageWorkflowState;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.mcp.tool.comfyui.ImageGenerationTools.ReferenceImageInput;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.service.comfyui.ComfyWorkflowRegistry;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphArgs;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageGenerationToolsTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    private static final String BASE_WORKFLOW = """
            {"6": {"inputs": {"text": "__PROMPT__"}}}""";

    @Mock
    private CompiledGraph<ImageWorkflowState> imageWorkflowGraph;

    @Mock
    private GraphRunner graphRunner;

    @Mock
    private ComfyWorkflowRegistry comfyWorkflowRegistry;

    @Mock
    private WorkflowImageGenerationService workflowImageGenerationService;

    @Mock
    private McpSyncRequestContext mcpSyncRequestContext;

    private RegisteredComfyWorkflow generateImage;

    private ImageGenerationTools imageGenerationTools;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(CALLER.userId().toString())
                .issuedAt(Instant.now())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        ComfyWorkflowTemplate baseTemplate = ComfyWorkflowTemplate.parse(BASE_WORKFLOW, JsonMapper.builder().build());
        generateImage = new RegisteredComfyWorkflow("generate_image", "Example", "General.", baseTemplate);

        imageGenerationTools = new ImageGenerationTools(
                imageWorkflowGraph, graphRunner, comfyWorkflowRegistry, workflowImageGenerationService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Decoding sits behind the service's role check, so the raw argument from the request is handed
     * on untouched rather than the typed copy Spring AI built for the method parameter.
     */
    @Test
    void generateImageAgent_passesTheRawReferenceImagesAndTheEnhancedPromptToTheService() {
        List<Map<String, String>> rawReferenceImages = List.of(Map.of("data", "not*base64!", "mimeType", "image/png"));
        when(mcpSyncRequestContext.request()).thenReturn(CallToolRequest.builder("generate_image_agent")
                .arguments(Map.of("prompt", "a watercolor", "referenceImages", rawReferenceImages))
                .build());
        stubGraphSelecting(new ImageWorkflowSelection("generate_image", "an enhanced watercolor", "General purpose."));
        when(comfyWorkflowRegistry.registeredWorkflows()).thenReturn(List.of(generateImage));
        when(workflowImageGenerationService.generate(same(generateImage), any(ImageGenerationRequest.class),
                same(rawReferenceImages), any()))
                .thenReturn(CallToolResult.builder().addTextContent("Generated with Example.").build());

        CallToolResult callToolResult = imageGenerationTools.generateImageAgent(mcpSyncRequestContext, "a watercolor",
                null, 768, List.of(new ReferenceImageInput("not*base64!", "image/png", "  a mountain lake at sunrise  ")));

        ArgumentCaptor<ImageGenerationRequest> requestCaptor = ArgumentCaptor.forClass(ImageGenerationRequest.class);
        verify(workflowImageGenerationService).generate(same(generateImage), requestCaptor.capture(),
                same(rawReferenceImages), any());
        assertThat(requestCaptor.getValue().prompt())
                .isEqualTo("an enhanced watercolor")
                .doesNotContain("mountain lake");

        ArgumentCaptor<GraphInput> graphInputCaptor = ArgumentCaptor.forClass(GraphInput.class);
        verify(graphRunner).run(eq(imageWorkflowGraph), graphInputCaptor.capture(), any(RunnableConfig.class),
                ArgumentMatchers.any());
        assertThat(graphInputCaptor.getValue()).isInstanceOfSatisfying(GraphArgs.class, graphArgs ->
                assertThat(graphArgs.value())
                        .containsEntry(ImageWorkflowState.REFERENCE_IMAGE_COUNT, 1)
                        .containsEntry(ImageWorkflowState.REFERENCE_IMAGE_DESCRIPTIONS, List.of("a mountain lake at sunrise")));
        assertThat(requestCaptor.getValue().width()).isEqualTo(ImageGenerationRequest.DEFAULT_WIDTH);
        assertThat(requestCaptor.getValue().height()).isEqualTo(768);

        assertThat(callToolResult.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(texts(callToolResult)).containsExactly(
                "Generated with Example.",
                "Workflow: generate_image. Why: General purpose.");
    }

    @Test
    void generateImageAgent_runsTheGraphForTheCallerWithTheImageCountOnly() {
        when(mcpSyncRequestContext.request()).thenReturn(CallToolRequest.builder("generate_image_agent")
                .arguments(Map.of("prompt", "a lighthouse"))
                .build());
        stubGraphSelecting(new ImageWorkflowSelection("generate_image", "a lighthouse", "General."));
        when(comfyWorkflowRegistry.registeredWorkflows()).thenReturn(List.of(generateImage));
        when(workflowImageGenerationService.generate(any(RegisteredComfyWorkflow.class), any(ImageGenerationRequest.class),
                any(), any()))
                .thenReturn(CallToolResult.builder().addTextContent("done").build());

        imageGenerationTools.generateImageAgent(mcpSyncRequestContext, "a lighthouse", null, null, null);

        ArgumentCaptor<GraphInput> graphInputCaptor = ArgumentCaptor.forClass(GraphInput.class);
        ArgumentCaptor<RunnableConfig> runnableConfigCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(graphRunner).run(eq(imageWorkflowGraph), graphInputCaptor.capture(), runnableConfigCaptor.capture(),
                ArgumentMatchers.any());

        assertThat(graphInputCaptor.getValue()).isInstanceOfSatisfying(GraphArgs.class, graphArgs ->
                assertThat(graphArgs.value())
                        .containsOnlyKeys(ImageWorkflowState.USER_PROMPT, ImageWorkflowState.REFERENCE_IMAGE_COUNT,
                                ImageWorkflowState.REFERENCE_IMAGE_DESCRIPTIONS, ImageWorkflowState.CALLER_IDENTITY)
                        .containsEntry(ImageWorkflowState.REFERENCE_IMAGE_COUNT, 0)
                        .containsEntry(ImageWorkflowState.REFERENCE_IMAGE_DESCRIPTIONS, List.of())
                        .containsEntry(ImageWorkflowState.CALLER_IDENTITY, CALLER));
        assertThat(runnableConfigCaptor.getValue().threadId()).hasValueSatisfying(threadId ->
                assertThat(threadId).startsWith("image-workflow:" + CALLER.userId() + ":"));
    }

    @Test
    void generateImageAgent_blankPrompt_isRejectedBeforeAnyModelCall() {
        CallToolResult callToolResult = imageGenerationTools.generateImageAgent(mcpSyncRequestContext, "  ", null, null, null);

        assertThat(callToolResult.isError()).isTrue();
        assertThat(texts(callToolResult)).singleElement().asString().contains("non-empty prompt");
        verifyNoInteractions(graphRunner, workflowImageGenerationService);
    }

    @Test
    void generateImageAgent_selectedWorkflowIsNotRegistered_failsClearly() {
        stubGraphSelecting(new ImageWorkflowSelection("retired_workflow", "a lighthouse", "Old."));
        when(comfyWorkflowRegistry.registeredWorkflows()).thenReturn(List.of(generateImage));

        CallToolResult callToolResult = imageGenerationTools.generateImageAgent(mcpSyncRequestContext, "a lighthouse", null, null, null);

        assertThat(callToolResult.isError()).isTrue();
        assertThat(texts(callToolResult)).singleElement().asString().contains("retired_workflow");
        verifyNoInteractions(workflowImageGenerationService);
    }

    @Test
    void generateImageAgent_imagesThatCannotBeReadRaw_failRatherThanGeneratingWithoutThem() {
        CallToolResult callToolResult = imageGenerationTools.generateImageAgent(mcpSyncRequestContext, "a watercolor",
                null, null, List.of(new ReferenceImageInput("iVBORw0KGgo", "image/png", null)));

        assertThat(callToolResult.isError()).isTrue();
        assertThat(texts(callToolResult)).singleElement().asString().contains("could not be read");
        verifyNoInteractions(graphRunner, workflowImageGenerationService);
    }

    /**
     * The schema is generated from the method signature, so the wire name and item shape of
     * {@code referenceImages} are pinned here; the request context must not appear in it.
     */
    @Test
    void inputSchema_advertisesThePromptAndAnArrayOfReferenceImageObjects() throws Exception {
        Method method = ImageGenerationTools.class.getMethod("generateImageAgent", McpSyncRequestContext.class,
                String.class, Integer.class, Integer.class, List.class);

        JsonNode schema = JsonMapper.builder().build().readTree(McpJsonSchemaGenerator.generateForMethodInput(method));

        assertThat(schema.path("properties").propertyNames())
                .containsExactlyInAnyOrder("prompt", "width", "height", "referenceImages");
        assertThat(schema.path("required").toString()).isEqualTo("[\"prompt\"]");

        JsonNode referenceImages = schema.path("properties").path("referenceImages");
        assertThat(referenceImages.path("type").asString()).isEqualTo("array");
        assertThat(referenceImages.path("items").path("properties").propertyNames())
                .containsExactlyInAnyOrder("data", "mimeType", "description");
        assertThat(referenceImages.path("items").path("required").toString())
                .contains("data", "mimeType")
                .doesNotContain("description");
    }

    private void stubGraphSelecting(ImageWorkflowSelection imageWorkflowSelection) {
        ImageWorkflowState finalState = new ImageWorkflowState(Map.of(
                ImageWorkflowState.WORKFLOW_SELECTION, imageWorkflowSelection));

        when(graphRunner.run(eq(imageWorkflowGraph), any(GraphInput.class), any(RunnableConfig.class),
                ArgumentMatchers.any()))
                .thenReturn(NodeOutput.of(END, finalState));
    }

    private static List<String> texts(CallToolResult callToolResult) {
        return callToolResult.content().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .toList();
    }
}
