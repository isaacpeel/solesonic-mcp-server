package com.solesonic.model.google.gmail;

/**
 * A single message's decoded text, with enough header context that the caller doesn't have to
 * correlate the result back against a separate listing call to know what it is reading.
 * <p>
 * {@code body} is returned verbatim, exactly as the chosen MIME part carried it — this server does
 * not convert HTML to plain text. {@code mimeType} names what {@code body} actually is, so a caller
 * receiving {@code text/html} knows it is looking at markup. Both are {@code null} when the message
 * has no readable text part at all, or when the only candidate parts failed to decode.
 */
public record GmailMessageBody(String id,
                               String subject,
                               String from,
                               String date,
                               String mimeType,
                               String body) {

    public GmailMessageBody {
        if ((mimeType == null) != (body == null)) {
            throw new IllegalArgumentException(
                    "mimeType and body describe the same text part, so they are set or null together");
        }
    }
}
