package com.solesonic.model.google.gmail;

import java.util.List;

/**
 * A node in Gmail's MIME tree. {@code format=metadata} populates {@code headers} only, while
 * {@code format=full} also fills in {@code mimeType}, and then either {@code body} (a leaf part) or
 * {@code parts} (a multipart container) — a {@code multipart/alternative} wrapping a
 * {@code text/plain} and a {@code text/html} sibling is the common shape. Attachment ids and the
 * remaining Gmail fields are still ignored; the shared {@code JsonMapper} skips unknown properties.
 */
public record GmailMessagePart(String mimeType,
                               List<GmailHeader> headers,
                               GmailMessagePartBody body,
                               List<GmailMessagePart> parts) {
}
