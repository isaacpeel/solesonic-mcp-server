package com.solesonic.mcp.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class ProgressReporter {
    private static final Logger log = LoggerFactory.getLogger(ProgressReporter.class);

    private final BiConsumer<Integer, String> emitter;
    private final AtomicInteger lastPercent;

    public ProgressReporter(McpAsyncRequestContext mcpAsyncRequestContext) {
        Objects.requireNonNull(mcpAsyncRequestContext, "mcpSyncRequestContext must not be null");
        this.emitter = (percent, message) -> mcpAsyncRequestContext
                .progress(progress -> progress.percentage(percent).message(message))
                .subscribe();
        this.lastPercent = new AtomicInteger(0);
    }

    ProgressReporter(BiConsumer<Integer, String> emitter) {
        this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
        this.lastPercent = new AtomicInteger(0);
    }

    public void emit(int requestedPercent, String message) {
        int clamped = Math.clamp(requestedPercent, 0, 100);
        int monotonic = lastPercent.updateAndGet(previous -> Math.max(previous, clamped));
        String safeMessage = message == null ? "" : message;

        log.info("Emitting notification: {} at percent: {}", safeMessage, monotonic);

        emitter.accept(monotonic, safeMessage);
    }
}