package com.solesonic.mcp.tool.xero;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.mcp.exception.google.GoogleReconnectRequiredException;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.google.gmail.GmailMessageSummary;
import com.solesonic.model.xero.XeroProposal;
import com.solesonic.service.google.GmailMessageService;
import com.solesonic.service.xero.XeroProposalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XeroToolsTest {

    @Mock
    private GmailMessageService gmailMessageService;

    @Mock
    private XeroProposalService xeroProposalService;

    @Mock
    private McpSyncRequestContext mcpSyncRequestContext;

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    @BeforeEach
    void authenticateCaller() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(CALLER.userId().toString())
                .issuedAt(Instant.now())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void convertEmailToXeroProposal_formatsTheMockedProposal() {
        GmailMessageSummary emailSummary = new GmailMessageSummary(
                "m1", "Invoice 42", "billing@example.com", "Sun, 17 Aug 2026");
        XeroProposal proposal = new XeroProposal(
                "MOCK-A1B2C3D4", "DRAFT", "Proposal: Invoice 42", "m1", "Invoice 42", "billing@example.com",
                "2026-08-23T10:00:00Z");

        when(gmailMessageService.getMessageSummary(CALLER, "m1")).thenReturn(emailSummary);
        when(xeroProposalService.createProposalFromEmail(emailSummary)).thenReturn(proposal);

        String result = new XeroTools(gmailMessageService, xeroProposalService)
                .convertEmailToXeroProposal(mcpSyncRequestContext, "m1");

        assertTrue(result.contains("[MOCK]"), result);
        assertTrue(result.contains("MOCK-A1B2C3D4"), result);
        assertTrue(result.contains("Invoice 42"), result);
        assertTrue(result.contains("billing@example.com"), result);
    }

    @Test
    void convertEmailToXeroProposal_asksTheUserToConnectGoogle_ratherThanFailing() {
        when(gmailMessageService.getMessageSummary(CALLER, "m1"))
                .thenThrow(new GoogleReconnectRequiredException("No Google grant"));

        String result = new XeroTools(gmailMessageService, xeroProposalService)
                .convertEmailToXeroProposal(mcpSyncRequestContext, "m1");

        assertTrue(result.contains("Connect Google in Solesonic settings"), result);
    }

    @Test
    void convertEmailToXeroProposal_reportsAMissingMessage_ratherThanFailing() {
        when(gmailMessageService.getMessageSummary(CALLER, "missing"))
                .thenThrow(new GmailException("Failed to read message missing: 404 NOT_FOUND"));

        String result = new XeroTools(gmailMessageService, xeroProposalService)
                .convertEmailToXeroProposal(mcpSyncRequestContext, "missing");

        assertEquals("Could not find a Gmail message with id 'missing'.", result);
    }
}
