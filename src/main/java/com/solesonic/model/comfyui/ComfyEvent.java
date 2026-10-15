package com.solesonic.model.comfyui;

import java.util.List;

/**
 * One message from ComfyUI's {@code /ws} event stream, reduced to the fields this server reads.
 *
 * <p>Every execution event carries the prompt id it belongs to: a socket only receives events for
 * prompts submitted with its {@code client_id}, but {@link QueueStatus} is broadcast to every socket
 * and {@link ExecutionInterrupted} can be.
 */
public sealed interface ComfyEvent {

    /**
     * {@code status}: sent on connect and whenever the queue changes. {@code queueRemaining} counts
     * every pending and running prompt, this one included.
     */
    record QueueStatus(int queueRemaining) implements ComfyEvent {}

    /**
     * {@code execution_start}: the prompt has left the queue.
     */
    record ExecutionStarted(String promptId) implements ComfyEvent {}

    /**
     * {@code execution_cached}: nodes whose output was already cached when the prompt started. They
     * never get a {@link NodeExecuting}.
     */
    record ExecutionCached(String promptId, List<String> nodeIds) implements ComfyEvent {}

    /**
     * {@code executing}: a node has started. A null {@code nodeId} means the whole prompt is done and
     * its history entry has been written.
     */
    record NodeExecuting(String promptId, String nodeId) implements ComfyEvent {}

    /**
     * {@code progress}: step progress inside a node, such as sampler steps.
     */
    record NodeProgress(String promptId, String nodeId, double value, double max) implements ComfyEvent {}

    /**
     * {@code executed}: a node has finished, cached nodes included.
     */
    record NodeExecuted(String promptId, String nodeId) implements ComfyEvent {}

    /**
     * {@code execution_success}: every node ran. The history entry is written after this, so it is
     * not yet the signal to read {@code /history}.
     */
    record ExecutionSucceeded(String promptId) implements ComfyEvent {}

    /**
     * {@code execution_error}: a node raised an exception.
     */
    record ExecutionFailed(String promptId, String nodeId, String nodeType, String exceptionMessage) implements ComfyEvent {}

    /**
     * {@code execution_interrupted}: the prompt was cancelled from the ComfyUI side.
     */
    record ExecutionInterrupted(String promptId, String nodeId, String nodeType) implements ComfyEvent {}

    /**
     * Not a ComfyUI message: the socket closed or failed, so no further events will arrive.
     */
    record Disconnected(String reason) implements ComfyEvent {}
}
