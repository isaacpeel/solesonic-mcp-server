package com.solesonic.mcp.tool.google;

import com.solesonic.mcp.exception.google.GoogleReconnectRequiredException;
import com.solesonic.model.google.gmail.GmailMessageSummary;
import com.solesonic.service.google.GmailMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@SuppressWarnings("unused")
@Service
public class GmailTools {

    private static final Logger log = LoggerFactory.getLogger(GmailTools.class);

    public static final String LIST_GMAIL_MESSAGES = "list_gmail_messages";

    private static final String LIST_GMAIL_MESSAGES_DESCRIPTION = """
            Lists the most recent messages in the user's Gmail inbox, newest first.
            Returns the subject, sender, and date of each message. Does not return message bodies.
            """;

    static final int DEFAULT_MAX_RESULTS = 10;
    static final int MINIMUM_MAX_RESULTS = 1;

    /**
     * Gmail returns ids only, so every message costs its own request. The cap keeps a single tool
     * call from turning into an unbounded run of them.
     */
    static final int MAXIMUM_MAX_RESULTS = 25;

    private static final String RECONNECT_MESSAGE = """
            Your Google account isn't connected, so the inbox can't be read. \
            Connect Google in Solesonic settings and try again.""";

    private static final String EMPTY_INBOX_MESSAGE = "There are no messages in the inbox.";

    private final GmailMessageService gmailMessageService;

    public GmailTools(GmailMessageService gmailMessageService) {
        this.gmailMessageService = gmailMessageService;
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-GMAIL-LIST')")
    @McpTool(name = LIST_GMAIL_MESSAGES, description = LIST_GMAIL_MESSAGES_DESCRIPTION)
    public String listGmailMessages(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = "How many messages to list. Defaults to 10, maximum 25.",
                    required = false) Integer maxResults
    ) {
        int requestedResults = clamp(maxResults);

        mcpSyncRequestContext.log(logging ->
                logging.message("Listing the " + requestedResults + " most recent inbox messages"));

        List<GmailMessageSummary> summaries;

        try {
            summaries = gmailMessageService.listInboxMessages(requestedResults);
        } catch (GoogleReconnectRequiredException googleReconnectRequiredException) {
            log.info("Gmail listing skipped - {}", googleReconnectRequiredException.getMessage());

            return RECONNECT_MESSAGE;
        }

        if (summaries.isEmpty()) {
            return EMPTY_INBOX_MESSAGE;
        }

        return format(summaries);
    }

    static int clamp(Integer maxResults) {
        if (maxResults == null) {
            return DEFAULT_MAX_RESULTS;
        }

        return Math.clamp(maxResults, MINIMUM_MAX_RESULTS, MAXIMUM_MAX_RESULTS);
    }

    private String format(List<GmailMessageSummary> summaries) {
        StringBuilder messageBuilder = new StringBuilder(
                "The %d most recent inbox messages, newest first:".formatted(summaries.size()));

        for (int position = 0; position < summaries.size(); position++) {
            GmailMessageSummary summary = summaries.get(position);

            messageBuilder.append("\n%d. %s — from %s".formatted(
                    position + 1, summary.subject(), summary.from()));

            if (!summary.date().isBlank()) {
                messageBuilder.append(" (%s)".formatted(summary.date()));
            }
        }

        return messageBuilder.toString();
    }
}
