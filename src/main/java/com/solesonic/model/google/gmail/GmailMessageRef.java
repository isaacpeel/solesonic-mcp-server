package com.solesonic.model.google.gmail;

/** An entry in a {@code users.messages.list} response: ids only, no content. */
public record GmailMessageRef(String id, String threadId) {
}
