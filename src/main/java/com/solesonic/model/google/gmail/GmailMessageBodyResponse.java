package com.solesonic.model.google.gmail;

/**
 * Shape returned by the Gmail body tool, serialized to JSON by the MCP framework.
 * <p>
 * {@code note} follows the {@link GmailMessageListResponse} convention — a human-readable
 * explanation when there is nothing to show, {@code null} on a normal result. It widens that
 * convention in exactly one case: {@link #truncated(String, GmailMessageBody)} carries a note
 * <em>and</em> the message, because a body cut to the caller's character limit must still be
 * returned while telling the model it is only part of the message.
 */
public record GmailMessageBodyResponse(String note, GmailMessageBody message) {

    public static GmailMessageBodyResponse of(GmailMessageBody message) {
        return new GmailMessageBodyResponse(null, message);
    }

    public static GmailMessageBodyResponse note(String note) {
        return new GmailMessageBodyResponse(note, null);
    }

    /** A real body that was shortened — the only result carrying both a note and data. */
    public static GmailMessageBodyResponse truncated(String note, GmailMessageBody message) {
        return new GmailMessageBodyResponse(note, message);
    }
}
