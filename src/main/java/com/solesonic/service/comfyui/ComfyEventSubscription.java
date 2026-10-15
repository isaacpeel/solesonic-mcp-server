package com.solesonic.service.comfyui;

import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ComfyEvent;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The events of one open {@code /ws} connection, handed from the socket's thread to the request
 * thread through a queue. Events are consumed on the request thread so progress notifications are
 * emitted from the same thread as before, never from a Netty event loop.
 */
public final class ComfyEventSubscription implements AutoCloseable {

    private final BlockingQueue<ComfyEvent> events = new LinkedBlockingQueue<>();
    private final Disposable connection;

    /**
     * @param connection disposed by {@link #close()} to shut the socket
     */
    public ComfyEventSubscription(Disposable connection) {
        this.connection = connection;
    }

    public void publish(ComfyEvent comfyEvent) {
        events.add(comfyEvent);
    }

    /**
     * The next event, or empty if none arrives within {@code timeout}.
     */
    public Optional<ComfyEvent> next(Duration timeout) {
        try {
            return Optional.ofNullable(events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new ComfyUiException("Interrupted while waiting for ComfyUI events", interruptedException);
        }
    }

    @Override
    public void close() {
        connection.dispose();
    }
}
