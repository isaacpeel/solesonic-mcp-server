package com.solesonic.model.google.gmail;

/**
 * The content of a leaf MIME part. {@code data} is base64url-encoded — the URL-safe alphabet, so it
 * must be decoded with {@link java.util.Base64#getUrlDecoder()} and never the standard decoder.
 * Gmail omits {@code data} for attachment parts, which carry an {@code attachmentId} instead.
 */
public record GmailMessagePartBody(String data, Integer size) {
}
