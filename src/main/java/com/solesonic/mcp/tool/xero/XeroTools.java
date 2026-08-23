package com.solesonic.mcp.tool.xero;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.mcp.exception.google.GoogleReconnectRequiredException;
import com.solesonic.model.google.gmail.GmailMessageSummary;
import com.solesonic.model.xero.XeroProposal;
import com.solesonic.service.google.GmailMessageService;
import com.solesonic.service.xero.XeroProposalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@SuppressWarnings("unused")
@Service
public class XeroTools {

    private static final Logger log = LoggerFactory.getLogger(XeroTools.class);

    public static final String CONVERT_EMAIL_TO_XERO_PROPOSAL = "convert_email_to_xero_proposal";

    private static final String CONVERT_EMAIL_TO_XERO_PROPOSAL_DESCRIPTION = """
            Converts a single Gmail message into a draft Xero proposal. This is a mocked integration —
            no real Xero account is contacted. Use a Gmail message id returned by list_gmail_messages or
            list_gmail_messages_by_label.
            """;

    private static final String RECONNECT_MESSAGE = """
            Your Google account isn't connected, so the email can't be read. \
            Connect Google in Solesonic settings and try again.""";

    private static final String MESSAGE_NOT_FOUND_MESSAGE = "Could not find a Gmail message with id '%s'.";

    private final GmailMessageService gmailMessageService;
    private final XeroProposalService xeroProposalService;

    public XeroTools(GmailMessageService gmailMessageService, XeroProposalService xeroProposalService) {
        this.gmailMessageService = gmailMessageService;
        this.xeroProposalService = xeroProposalService;
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-XERO')")
    @McpTool(name = CONVERT_EMAIL_TO_XERO_PROPOSAL, description = CONVERT_EMAIL_TO_XERO_PROPOSAL_DESCRIPTION)
    public String convertEmailToXeroProposal(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = "The Gmail message id to convert, taken from a prior list_gmail_messages or list_gmail_messages_by_label result.")
            String messageId
    ) {
        mcpSyncRequestContext.log(logging ->
                logging.message("Converting Gmail message '" + messageId + "' into a Xero proposal"));

        GmailMessageSummary emailSummary;

        try {
            emailSummary = gmailMessageService.getMessageSummary(messageId);
        } catch (GoogleReconnectRequiredException googleReconnectRequiredException) {
            log.info("Xero proposal conversion skipped - {}", googleReconnectRequiredException.getMessage());

            return RECONNECT_MESSAGE;
        } catch (GmailException gmailException) {
            log.info("Xero proposal conversion skipped - {}", gmailException.getMessage());

            return MESSAGE_NOT_FOUND_MESSAGE.formatted(messageId);
        }

        XeroProposal proposal = xeroProposalService.createProposalFromEmail(emailSummary);

        return format(proposal);
    }

    private String format(XeroProposal proposal) {
        return """
                [MOCK] Created draft Xero proposal %s (status: %s)
                Title: %s
                Based on email "%s" from %s
                This is a mocked response — no proposal was created in a real Xero account.""".formatted(
                proposal.proposalId(),
                proposal.status(),
                proposal.title(),
                proposal.sourceEmailSubject(),
                proposal.sourceEmailFrom());
    }
}
