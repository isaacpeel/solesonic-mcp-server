package com.solesonic.mcp.exception;

/**
 * A tool failure whose message is guaranteed to survive the MCP error surface.
 *
 * <p>This exception deliberately has no cause-accepting constructor. Spring AI's MCP tool
 * callback renders the caller-visible error from the <em>deepest</em> cause in the chain, so
 * chaining an exception that carries no message — StringTemplate's {@code STException}, a bare
 * {@code NullPointerException}, {@code Objects.requireNonNull} without a message — would put the
 * literal text {@code "null"} in front of the calling model again. {@link ToolFailures} flattens
 * the whole chain into this exception's own message instead.
 */
public class McpToolFailureException extends RuntimeException {

    public McpToolFailureException(String message) {
        super(message);
    }
}
