package com.solesonic.model.google.gmail;

import java.util.List;

/**
 * Uniform shape returned by both Gmail listing tools, serialized to JSON by the MCP framework.
 * {@code note} carries a human-readable explanation when there's nothing to show — no Google
 * connection, an unmatched label, no messages — and is {@code null} on a normal result. The model
 * gets the full per-message data (including the Gmail id) and decides how to present it; this
 * server does not pre-format the result as prose or Markdown.
 */
public record GmailMessageListResponse(String note, List<GmailMessageSummary> messages) {

    public static GmailMessageListResponse of(List<GmailMessageSummary> messages) {
        return new GmailMessageListResponse(null, messages);
    }

    public static GmailMessageListResponse note(String note) {
        return new GmailMessageListResponse(note, List.of());
    }
}
