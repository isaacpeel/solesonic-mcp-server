package com.solesonic.mcp.tool.google;

import com.solesonic.mcp.exception.google.GmailLabelNotFoundException;
import com.solesonic.mcp.exception.google.GmailMessageNotFoundException;
import com.solesonic.mcp.exception.google.GoogleReconnectRequiredException;
import com.solesonic.model.google.gmail.GmailMessageBody;
import com.solesonic.model.google.gmail.GmailMessageBodyResponse;
import com.solesonic.model.google.gmail.GmailMessageListResponse;
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
            Lists the most recent messages in the user's Gmail inbox, newest first. Returns each
            message's id, subject, sender, and date as structured data. The id is the unique handle
            for that exact message — always surface it in your reply (e.g. as a labeled field or
            inline code) even when you format the rest freely, since the user needs it to reference
            or act on a specific message afterward. Does not return message bodies.
            """;

    public static final String LIST_GMAIL_MESSAGES_BY_LABEL = "list_gmail_messages_by_label";

    private static final String LIST_GMAIL_MESSAGES_BY_LABEL_DESCRIPTION = """
            Lists the most recent messages under a specific Gmail label, newest first — e.g. "STARRED",
            "IMPORTANT", or the name of a user-created label. Returns each message's id, subject,
            sender, and date as structured data. The id is the unique handle for that exact message —
            always surface it in your reply (e.g. as a labeled field or inline code) even when you
            format the rest freely, since the user needs it to reference or act on a specific message
            afterward. Does not return message bodies.
            """;

    public static final String GET_GMAIL_MESSAGE_BODY = "get_gmail_message_body";

    private static final String GET_GMAIL_MESSAGE_BODY_DESCRIPTION = """
            Retrieves the body of a single Gmail message by id. Use this after list_gmail_messages or
            list_gmail_messages_by_label has surfaced the message's id — those tools return subject,
            sender and date only, never body content. The body comes back exactly as the message
            carried it, alongside a mimeType saying what it is: "text/plain" for an ordinary message,
            or "text/html" when the message has no plain-text part, in which case the body is raw
            markup to read through rather than repeat back verbatim. A body longer than the character
            limit is cut short, and the note says so.
            """;

    static final int DEFAULT_MAX_RESULTS = 10;
    static final int MINIMUM_MAX_RESULTS = 1;

    /**
     * Gmail returns ids only, so every message costs its own request. The cap keeps a single tool
     * call from turning into an unbounded run of them.
     */
    static final int MAXIMUM_MAX_RESULTS = 25;

    static final int DEFAULT_MAX_CHARACTERS = 20_000;
    static final int MINIMUM_MAX_CHARACTERS = 1_000;

    /**
     * A body goes straight into a model's context, where an unbounded newsletter or a thread of
     * quoted history has a real cost. This caps what is *returned*, the way MAXIMUM_MAX_RESULTS caps
     * listing — the message is still fetched and decoded in full before it is trimmed.
     */
    static final int MAXIMUM_MAX_CHARACTERS = 100_000;

    private static final String RECONNECT_MESSAGE = """
            Your Google account isn't connected, so the inbox can't be read. \
            Connect Google in Solesonic settings and try again.""";

    private static final String EMPTY_INBOX_MESSAGE = "There are no messages in the inbox.";

    private static final String NO_MATCHING_LABEL_MESSAGE = "No Gmail label named '%s' was found.";
    private static final String EMPTY_LABEL_MESSAGE = "There are no messages under the label '%s'.";

    private static final String NO_MATCHING_MESSAGE = "No Gmail message found with id '%s'.";
    private static final String NO_TEXT_BODY_MESSAGE = "Message '%s' has no readable text body.";
    private static final String TRUNCATED_BODY_MESSAGE =
            "The body was longer than %d characters, so it has been truncated.";

    private final GmailMessageService gmailMessageService;

    public GmailTools(GmailMessageService gmailMessageService) {
        this.gmailMessageService = gmailMessageService;
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-GMAIL-LIST')")
    @McpTool(name = LIST_GMAIL_MESSAGES, description = LIST_GMAIL_MESSAGES_DESCRIPTION)
    public GmailMessageListResponse listGmailMessages(
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

            return GmailMessageListResponse.note(RECONNECT_MESSAGE);
        }

        if (summaries.isEmpty()) {
            return GmailMessageListResponse.note(EMPTY_INBOX_MESSAGE);
        }

        return GmailMessageListResponse.of(summaries);
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-GMAIL-LIST')")
    @McpTool(name = LIST_GMAIL_MESSAGES_BY_LABEL, description = LIST_GMAIL_MESSAGES_BY_LABEL_DESCRIPTION)
    public GmailMessageListResponse listGmailMessagesByLabel(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = "The Gmail label to filter by, e.g. \"STARRED\" or a custom label name.")
            String label,
            @McpToolParam(description = "How many messages to list. Defaults to 10, maximum 25.",
                    required = false) Integer maxResults
    ) {
        int requestedResults = clamp(maxResults);

        mcpSyncRequestContext.log(logging ->
                logging.message("Listing the " + requestedResults + " most recent messages labeled '" + label + "'"));

        List<GmailMessageSummary> summaries;

        try {
            summaries = gmailMessageService.listMessagesByLabel(label, requestedResults);
        } catch (GoogleReconnectRequiredException googleReconnectRequiredException) {
            log.info("Gmail listing skipped because reconnect is required - {}", googleReconnectRequiredException.getMessage());

            return GmailMessageListResponse.note(RECONNECT_MESSAGE);
        } catch (GmailLabelNotFoundException gmailLabelNotFoundException) {
            log.info("Gmail listing skipped because the label isn't found - {}", gmailLabelNotFoundException.getMessage());

            return GmailMessageListResponse.note(NO_MATCHING_LABEL_MESSAGE.formatted(label));
        }

        if (summaries.isEmpty()) {
            return GmailMessageListResponse.note(EMPTY_LABEL_MESSAGE.formatted(label));
        }

        GmailMessageListResponse gmailMessageListResponse = GmailMessageListResponse.of(summaries);
        log.info("Returning response with {} messages", gmailMessageListResponse.messages().size());
        return gmailMessageListResponse;

    }

    @PreAuthorize("hasAuthority('ROLE_MCP-GMAIL-LIST')")
    @McpTool(name = GET_GMAIL_MESSAGE_BODY, description = GET_GMAIL_MESSAGE_BODY_DESCRIPTION)
    public GmailMessageBodyResponse getGmailMessageBody(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = "The Gmail message id, as returned by list_gmail_messages or list_gmail_messages_by_label.")
            String messageId,
            @McpToolParam(description = "Maximum characters of body text to return. Defaults to 20000, maximum 100000.",
                    required = false) Integer maxCharacters
    ) {
        int characterLimit = clampCharacters(maxCharacters);

        mcpSyncRequestContext.log(logging ->
                logging.message("Reading the body of message '" + messageId + "'"));

        GmailMessageBody messageBody;

        try {
            messageBody = gmailMessageService.getMessageBody(messageId);
        } catch (GoogleReconnectRequiredException googleReconnectRequiredException) {
            log.info("Gmail body read skipped because reconnect is required - {}", googleReconnectRequiredException.getMessage());

            return GmailMessageBodyResponse.note(RECONNECT_MESSAGE);
        } catch (GmailMessageNotFoundException gmailMessageNotFoundException) {
            log.info("Gmail body read skipped because the message isn't found - {}", gmailMessageNotFoundException.getMessage());

            return GmailMessageBodyResponse.note(NO_MATCHING_MESSAGE.formatted(messageId));
        }

        if (messageBody.body() == null) {
            return GmailMessageBodyResponse.note(NO_TEXT_BODY_MESSAGE.formatted(messageId));
        }

        if (messageBody.body().length() <= characterLimit) {
            return GmailMessageBodyResponse.of(messageBody);
        }

        GmailMessageBody truncatedBody = new GmailMessageBody(
                messageBody.id(),
                messageBody.subject(),
                messageBody.from(),
                messageBody.date(),
                messageBody.mimeType(),
                truncate(messageBody.body(), characterLimit));

        log.info("Truncated the body of message {} to {} characters", messageId, characterLimit);

        return GmailMessageBodyResponse.truncated(TRUNCATED_BODY_MESSAGE.formatted(characterLimit), truncatedBody);
    }

    /**
     * Cuts the body to the limit without splitting a surrogate pair. {@code substring} counts UTF-16
     * code units, so a boundary landing inside an emoji would otherwise leave a trailing unpaired
     * surrogate that a strict JSON encoder mangles or rejects.
     */
    static String truncate(String body, int characterLimit) {
        int endIndex = characterLimit;

        if (Character.isHighSurrogate(body.charAt(endIndex - 1))) {
            endIndex--;
        }

        return body.substring(0, endIndex);
    }

    static int clampCharacters(Integer maxCharacters) {
        if (maxCharacters == null) {
            return DEFAULT_MAX_CHARACTERS;
        }

        return Math.clamp(maxCharacters, MINIMUM_MAX_CHARACTERS, MAXIMUM_MAX_CHARACTERS);
    }

    static int clamp(Integer maxResults) {
        if (maxResults == null) {
            return DEFAULT_MAX_RESULTS;
        }

        return Math.clamp(maxResults, MINIMUM_MAX_RESULTS, MAXIMUM_MAX_RESULTS);
    }
}
