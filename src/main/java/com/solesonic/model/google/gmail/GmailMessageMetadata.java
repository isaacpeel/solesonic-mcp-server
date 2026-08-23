package com.solesonic.model.google.gmail;

public record GmailMessageMetadata(String id,
                                   String threadId,
                                   String snippet,
                                   GmailMessagePart payload) {
}
