package com.solesonic.mcp.exception;

/**
 * Converts an arbitrary failure into one that always carries an actionable message across the
 * MCP boundary.
 *
 * <p>Spring AI's MCP tool callback builds the caller-visible error text from
 * {@code exception.getMessage() + lineSeparator + rootCause.getMessage()}, where {@code rootCause}
 * is the <em>deepest</em> cause in the chain. When that deepest cause carries no message the
 * calling model receives the literal text {@code "null"}, learns nothing from the failure, and
 * retries the same broken call — the observed symptom was eight identical {@code agile_workflow}
 * retries over roughly two minutes, all rooted in a message-less
 * {@code org.stringtemplate.v4.compiler.STException}.
 *
 * <p>Tool entry points and graph nodes should log the original failure with its stack trace for
 * server-side diagnosis, then fail with {@link #describe(String, Throwable)} so the caller
 * receives something it can act on.
 */
public final class ToolFailures {

    private static final int MAX_CAUSE_DEPTH = 10;
    private static final String CAUSE_SEPARATOR = " caused by ";
    private static final String NO_EXCEPTION = "an unknown error with no exception attached";

    private ToolFailures() {
    }

    /**
     * Wraps a failure in an exception whose message names the operation and flattens the whole
     * cause chain, so no message-less cause can reach the caller.
     *
     * @param operation what was being attempted, phrased so it reads before " failed: "
     * @param failure   the original failure; may be null
     * @return an exception carrying a non-null, descriptive message
     */
    public static McpToolFailureException describe(String operation, Throwable failure) {
        if (failure instanceof McpToolFailureException alreadyDescribed) {
            return alreadyDescribed;
        }

        return new McpToolFailureException(operation + " failed: " + summarize(failure));
    }

    /**
     * Renders a failure and its cause chain as a single line, substituting the class name wherever
     * a link in the chain carries no message.
     */
    public static String summarize(Throwable failure) {
        if (failure == null) {
            return NO_EXCEPTION;
        }

        StringBuilder summary = new StringBuilder(describeThrowable(failure));
        Throwable currentFailure = failure;

        for (int depth = 0; depth < MAX_CAUSE_DEPTH; depth++) {
            Throwable cause = currentFailure.getCause();

            if (cause == null || cause == currentFailure) {
                break;
            }

            summary.append(CAUSE_SEPARATOR).append(describeThrowable(cause));
            currentFailure = cause;
        }

        return summary.toString();
    }

    private static String describeThrowable(Throwable throwable) {
        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            return throwable.getClass().getName();
        }

        return throwable.getClass().getName() + ": " + message;
    }
}
