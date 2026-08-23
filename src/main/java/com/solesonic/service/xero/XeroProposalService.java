package com.solesonic.service.xero;

import com.solesonic.model.google.gmail.GmailMessageSummary;
import com.solesonic.model.xero.XeroProposal;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Mocked — no real Xero account is contacted. This is the seam a future real integration replaces;
 * it deliberately takes an already-resolved {@link GmailMessageSummary} rather than a Gmail message
 * id, so it stays free of any Gmail dependency.
 */
@Service
public class XeroProposalService {

    public XeroProposal createProposalFromEmail(GmailMessageSummary emailSummary) {
        String proposalId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String title = "Proposal: %s".formatted(emailSummary.subject());

        return new XeroProposal(
                proposalId,
                "DRAFT",
                title,
                emailSummary.id(),
                emailSummary.subject(),
                emailSummary.from(),
                Instant.now().toString());
    }
}
