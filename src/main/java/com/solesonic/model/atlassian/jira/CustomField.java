package com.solesonic.model.atlassian.jira;

public record CustomField(
        boolean hasEpicLinkFieldDependency,
        boolean showField,
        NonEditableReason nonEditableReason
) {}

