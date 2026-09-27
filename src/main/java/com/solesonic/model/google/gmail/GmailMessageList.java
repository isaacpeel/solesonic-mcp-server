package com.solesonic.model.google.gmail;

import java.util.List;

/** Gmail omits {@code messages} entirely when nothing matches, so it can be {@code null}. */
public record GmailMessageList(List<GmailMessageRef> messages,
                               String nextPageToken,
                               Integer resultSizeEstimate) {
}
