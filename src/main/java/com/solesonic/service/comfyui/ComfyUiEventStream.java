package com.solesonic.service.comfyui;

/**
 * Opens ComfyUI's live event stream for one client id. A prompt submitted with that same
 * {@code client_id} reports its queue position, node execution and step progress on the stream.
 */
@FunctionalInterface
public interface ComfyUiEventStream {

    /**
     * Returns once ComfyUI has registered the connection, so no event for a prompt submitted
     * afterwards can be missed.
     *
     * @throws com.solesonic.mcp.exception.comfyui.ComfyUiException if the stream cannot be opened
     */
    ComfyEventSubscription open(String clientId);
}
