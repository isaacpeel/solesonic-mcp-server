package com.solesonic.model.google.gmail;

/** One inbox line as this server reports it. */
public record GmailMessageSummary(String id, String subject, String from, String date) {
}
