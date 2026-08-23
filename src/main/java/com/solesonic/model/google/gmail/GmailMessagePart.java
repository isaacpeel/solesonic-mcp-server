package com.solesonic.model.google.gmail;

import java.util.List;

/**
 * The slice of Gmail's {@code payload} this server reads. The real object carries body data, MIME
 * parts and attachment ids too; the shared {@code JsonMapper} ignores unknown properties, so
 * requesting {@code format=metadata} and binding only the headers is safe.
 */
public record GmailMessagePart(List<GmailHeader> headers) {
}
