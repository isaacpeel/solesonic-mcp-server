package com.solesonic.model.google.gmail;

import java.util.List;

/** Defensively nullable, matching every other list model in this package. */
public record GmailLabelList(List<GmailLabel> labels) {
}
