package com.solesonic.mcp.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolFailuresTest {

    /**
     * Mirrors Spring AI's {@code AbstractMcpToolMethodCallback#findCauseUsingPlainJava}: the
     * caller-visible error text is built from the message of the <em>deepest</em> cause.
     */
    private static Throwable deepestCause(Throwable throwable) {
        Throwable rootCause = throwable;

        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }

        return rootCause;
    }

    @Test
    void describe_messageLessRootCause_producesNonNullRootCauseMessage() {
        // The production failure: StringTemplate throws an STException carrying no message, wrapped
        // by Spring AI in an IllegalArgumentException that does carry one. The MCP layer reports
        // the deepest cause, so the useful wrapper message never reached the caller.
        Throwable messageLessRootCause = new IllegalStateException();
        Throwable wrapped = new IllegalArgumentException("The template string is not valid.", messageLessRootCause);

        McpToolFailureException described = ToolFailures.describe("Parsing the agile query intent", wrapped);

        assertThat(deepestCause(described).getMessage()).isNotNull();
    }

    @Test
    void describe_flattensTheWholeCauseChainIntoTheMessage() {
        Throwable messageLessRootCause = new IllegalStateException();
        Throwable wrapped = new IllegalArgumentException("The template string is not valid.", messageLessRootCause);

        McpToolFailureException described = ToolFailures.describe("Parsing the agile query intent", wrapped);

        assertThat(described.getMessage())
                .startsWith("Parsing the agile query intent failed: ")
                .contains("java.lang.IllegalArgumentException: The template string is not valid.")
                .contains("caused by java.lang.IllegalStateException");
    }

    @Test
    void describe_carriesNoCause_soTheDeepestCauseIsAlwaysItself() {
        McpToolFailureException described = ToolFailures.describe("Listing boards", new IllegalStateException());

        assertThat(described.getCause()).isNull();
        assertThat(deepestCause(described)).isSameAs(described);
    }

    @Test
    void describe_alreadyDescribedFailure_isNotWrappedTwice() {
        McpToolFailureException alreadyDescribed =
                ToolFailures.describe("Listing boards", new IllegalStateException("boom"));

        McpToolFailureException described = ToolFailures.describe("Parsing the agile query intent", alreadyDescribed);

        assertThat(described).isSameAs(alreadyDescribed);
    }

    @Test
    void describe_nullFailure_stillProducesAMessage() {
        McpToolFailureException described = ToolFailures.describe("Listing boards", null);

        assertThat(described.getMessage()).isNotNull().contains("Listing boards failed");
    }

    @Test
    void summarize_cyclicCauseChain_terminates() {
        Throwable first = new IllegalStateException("first");
        Throwable second = new IllegalStateException("second", first);
        first.initCause(second);

        String summary = ToolFailures.summarize(first);

        assertThat(summary).contains("first").contains("second");
    }

    @Test
    void summarize_blankMessage_fallsBackToTheClassName() {
        assertThat(ToolFailures.summarize(new IllegalStateException("   ")))
                .isEqualTo("java.lang.IllegalStateException");
    }
}
