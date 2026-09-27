package com.solesonic.model.xero;

/** Mocked — nothing here is backed by a real Xero account yet. */
public record XeroProposal(
        String proposalId,
        String status,
        String title,
        String sourceEmailId,
        String sourceEmailSubject,
        String sourceEmailFrom,
        String createdAt
) {
}
