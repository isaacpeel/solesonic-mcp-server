package com.solesonic.mcp.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards every StringTemplate prompt against a compilation failure at runtime.
 *
 * <p>Spring AI's {@link StTemplateRenderer} uses <code>{</code> and <code>}</code> as its
 * expression delimiters, so any literal brace in a prompt — a JSON example, most obviously — is
 * parsed as an expression. {@code jira_agile_prompt.st} shipped with JSON examples in it and
 * StringTemplate failed on the <code>[</code> inside them with
 * <code>52:31: '[' came as a complete surprise to me</code>, which took down every
 * {@code agile_workflow} call in production.
 *
 * <p>Validation is switched off deliberately: this asserts only that each template
 * <em>compiles</em>, not that every variable has a value, because the values are supplied by the
 * individual call sites.
 */
class PromptTemplateCompilationTest {

    private static final String PROMPT_RESOURCE_PATTERN = "classpath*:/prompt/**/*.st";

    private static List<Resource> promptResources() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(PROMPT_RESOURCE_PATTERN);

        return Arrays.asList(resources);
    }

    private static void compile(Resource promptResource) {
        StTemplateRenderer nonValidatingRenderer = StTemplateRenderer.builder()
                .validationMode(ValidationMode.NONE)
                .build();

        PromptTemplate promptTemplate = PromptTemplate.builder()
                .resource(promptResource)
                .renderer(nonValidatingRenderer)
                .build();

        promptTemplate.render();
    }

    @Test
    void promptResources_areDiscovered() throws IOException {
        assertThat(promptResources()).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("promptResources")
    void promptTemplate_compiles(Resource promptResource) {
        assertThatCode(() -> compile(promptResource)).doesNotThrowAnyException();
    }

    /**
     * Proves the check above actually bites — this is the exact shape that broke
     * {@code agile_workflow}. Without it, a guard that can never fail looks identical to one that
     * always passes.
     */
    @Test
    void promptTemplate_withJsonExample_failsToCompile() {
        Resource templateWithJsonExample = new ByteArrayResource("""
                Return a single JSON object:
                  {"issueKeys": ["IB-123"], "userIntent": "TRANSITION"}
                """.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> compile(templateWithJsonExample))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template string is not valid");
    }
}
