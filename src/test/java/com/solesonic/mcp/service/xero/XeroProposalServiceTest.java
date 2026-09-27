package com.solesonic.mcp.service.xero;

import com.solesonic.model.google.gmail.GmailMessageSummary;
import com.solesonic.model.xero.XeroProposal;
import com.solesonic.service.xero.XeroProposalService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XeroProposalServiceTest {

    private final XeroProposalService service = new XeroProposalService();

    @Test
    void createProposalFromEmail_mapsTheEmailFieldsThrough() {
        GmailMessageSummary emailSummary = new GmailMessageSummary(
                "m1", "Invoice 42", "billing@example.com", "Sun, 17 Aug 2026 12:00:00 -0500");

        XeroProposal proposal = service.createProposalFromEmail(emailSummary);

        assertEquals("m1", proposal.sourceEmailId());
        assertEquals("Invoice 42", proposal.sourceEmailSubject());
        assertEquals("billing@example.com", proposal.sourceEmailFrom());
        assertEquals("Proposal: Invoice 42", proposal.title());
    }

    @Test
    void createProposalFromEmail_generatesAMockPrefixedProposalId() {
        GmailMessageSummary emailSummary = new GmailMessageSummary("m1", "Invoice 42", "billing@example.com", "");

        XeroProposal proposal = service.createProposalFromEmail(emailSummary);

        assertTrue(proposal.proposalId().startsWith("MOCK-"), proposal.proposalId());
    }

    @Test
    void createProposalFromEmail_statusIsAlwaysDraft() {
        GmailMessageSummary emailSummary = new GmailMessageSummary("m1", "Invoice 42", "billing@example.com", "");

        XeroProposal proposal = service.createProposalFromEmail(emailSummary);

        assertEquals("DRAFT", proposal.status());
    }
}
