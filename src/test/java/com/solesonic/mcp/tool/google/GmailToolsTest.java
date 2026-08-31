package com.solesonic.mcp.tool.google;

import com.solesonic.mcp.exception.google.GmailLabelNotFoundException;
import com.solesonic.mcp.exception.google.GmailMessageNotFoundException;
import com.solesonic.mcp.exception.google.GoogleReconnectRequiredException;
import com.solesonic.model.google.gmail.GmailMessageBody;
import com.solesonic.model.google.gmail.GmailMessageBodyResponse;
import com.solesonic.model.google.gmail.GmailMessageListResponse;
import com.solesonic.model.google.gmail.GmailMessageSummary;
import com.solesonic.service.google.GmailMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import java.util.List;

import static com.solesonic.mcp.tool.google.GmailTools.DEFAULT_MAX_CHARACTERS;
import static com.solesonic.mcp.tool.google.GmailTools.DEFAULT_MAX_RESULTS;
import static com.solesonic.mcp.tool.google.GmailTools.MAXIMUM_MAX_CHARACTERS;
import static com.solesonic.mcp.tool.google.GmailTools.MAXIMUM_MAX_RESULTS;
import static com.solesonic.mcp.tool.google.GmailTools.MINIMUM_MAX_CHARACTERS;
import static com.solesonic.mcp.tool.google.GmailTools.MINIMUM_MAX_RESULTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GmailToolsTest {

    @Mock
    private GmailMessageService gmailMessageService;

    @Mock
    private McpSyncRequestContext mcpSyncRequestContext;

    @Test
    void clamp_appliesTheDefaultAndTheBounds() {
        assertEquals(DEFAULT_MAX_RESULTS, GmailTools.clamp(null));
        assertEquals(MINIMUM_MAX_RESULTS, GmailTools.clamp(0));
        assertEquals(MINIMUM_MAX_RESULTS, GmailTools.clamp(-5));
        assertEquals(MAXIMUM_MAX_RESULTS, GmailTools.clamp(500));
        assertEquals(7, GmailTools.clamp(7));
    }

    @Test
    void listGmailMessages_passesTheClampedCountToTheService() {
        when(gmailMessageService.listInboxMessages(anyInt())).thenReturn(List.of());

        new GmailTools(gmailMessageService).listGmailMessages(mcpSyncRequestContext, 500);

        verify(gmailMessageService).listInboxMessages(MAXIMUM_MAX_RESULTS);
    }

    @Test
    void listGmailMessages_returnsTheFullSummaryPerMessage_withNoNote() {
        List<GmailMessageSummary> summaries = List.of(
                new GmailMessageSummary("m1", "Standup notes", "Ada <ada@example.com>", "Mon, 18 Aug 2026"),
                new GmailMessageSummary("m2", "Invoice 42", "billing@example.com", ""));

        when(gmailMessageService.listInboxMessages(DEFAULT_MAX_RESULTS)).thenReturn(summaries);

        GmailMessageListResponse result = new GmailTools(gmailMessageService)
                .listGmailMessages(mcpSyncRequestContext, null);

        assertNull(result.note());
        assertEquals(summaries, result.messages());
    }

    @Test
    void listGmailMessages_reportsAnEmptyInbox_asANoteWithNoMessages() {
        when(gmailMessageService.listInboxMessages(DEFAULT_MAX_RESULTS)).thenReturn(List.of());

        GmailMessageListResponse result = new GmailTools(gmailMessageService)
                .listGmailMessages(mcpSyncRequestContext, null);

        assertEquals("There are no messages in the inbox.", result.note());
        assertEquals(List.of(), result.messages());
    }

    @Test
    void listGmailMessages_asksTheUserToConnectGoogle_ratherThanFailing() {
        when(gmailMessageService.listInboxMessages(DEFAULT_MAX_RESULTS))
                .thenThrow(new GoogleReconnectRequiredException("No Google grant"));

        GmailMessageListResponse result = new GmailTools(gmailMessageService)
                .listGmailMessages(mcpSyncRequestContext, null);

        assertTrue(result.note().contains("Connect Google in Solesonic settings"), result.note());
        assertEquals(List.of(), result.messages());
    }

    @Test
    void listGmailMessagesByLabel_passesTheClampedCountAndLabelToTheService() {
        when(gmailMessageService.listMessagesByLabel(eq("Receipts"), anyInt())).thenReturn(List.of());

        new GmailTools(gmailMessageService).listGmailMessagesByLabel(mcpSyncRequestContext, "Receipts", 500);

        verify(gmailMessageService).listMessagesByLabel("Receipts", MAXIMUM_MAX_RESULTS);
    }

    @Test
    void listGmailMessagesByLabel_returnsTheFullSummaryPerMessage_withNoNote() {
        List<GmailMessageSummary> summaries = List.of(
                new GmailMessageSummary("m1", "Your receipt", "billing@example.com", "Mon, 18 Aug 2026"));

        when(gmailMessageService.listMessagesByLabel("Receipts", DEFAULT_MAX_RESULTS)).thenReturn(summaries);

        GmailMessageListResponse result = new GmailTools(gmailMessageService)
                .listGmailMessagesByLabel(mcpSyncRequestContext, "Receipts", null);

        assertNull(result.note());
        assertEquals(summaries, result.messages());
    }

    @Test
    void listGmailMessagesByLabel_reportsAnEmptyLabel_asANoteWithNoMessages() {
        when(gmailMessageService.listMessagesByLabel("Receipts", DEFAULT_MAX_RESULTS)).thenReturn(List.of());

        GmailMessageListResponse result = new GmailTools(gmailMessageService)
                .listGmailMessagesByLabel(mcpSyncRequestContext, "Receipts", null);

        assertEquals("There are no messages under the label 'Receipts'.", result.note());
        assertEquals(List.of(), result.messages());
    }

    @Test
    void listGmailMessagesByLabel_asksTheUserToConnectGoogle_ratherThanFailing() {
        when(gmailMessageService.listMessagesByLabel("Receipts", DEFAULT_MAX_RESULTS))
                .thenThrow(new GoogleReconnectRequiredException("No Google grant"));

        GmailMessageListResponse result = new GmailTools(gmailMessageService)
                .listGmailMessagesByLabel(mcpSyncRequestContext, "Receipts", null);

        assertTrue(result.note().contains("Connect Google in Solesonic settings"), result.note());
    }

    @Test
    void listGmailMessagesByLabel_reportsAnUnrecognizedLabel_ratherThanFailing() {
        when(gmailMessageService.listMessagesByLabel("Nonexistent", DEFAULT_MAX_RESULTS))
                .thenThrow(new GmailLabelNotFoundException("No Gmail label named 'Nonexistent' was found"));

        GmailMessageListResponse result = new GmailTools(gmailMessageService)
                .listGmailMessagesByLabel(mcpSyncRequestContext, "Nonexistent", null);

        assertEquals("No Gmail label named 'Nonexistent' was found.", result.note());
    }

    // --- get_gmail_message_body -------------------------------------------------------------

    private GmailMessageBody messageBody(String body) {
        return new GmailMessageBody("m1", "Budget review", "ada@example.com",
                "Mon, 18 Aug 2026 09:00:00 -0500", "text/plain", body);
    }

    @Test
    void clampCharacters_appliesTheDefaultAndTheBounds() {
        assertEquals(DEFAULT_MAX_CHARACTERS, GmailTools.clampCharacters(null));
        assertEquals(MINIMUM_MAX_CHARACTERS, GmailTools.clampCharacters(0));
        assertEquals(MINIMUM_MAX_CHARACTERS, GmailTools.clampCharacters(-5));
        assertEquals(MAXIMUM_MAX_CHARACTERS, GmailTools.clampCharacters(500_000));
        assertEquals(5_000, GmailTools.clampCharacters(5_000));
    }

    @Test
    void getGmailMessageBody_passesTheMessageIdToTheService() {
        when(gmailMessageService.getMessageBody("m1")).thenReturn(messageBody("Short body"));

        new GmailTools(gmailMessageService).getGmailMessageBody(mcpSyncRequestContext, "m1", null);

        verify(gmailMessageService).getMessageBody("m1");
    }

    @Test
    void getGmailMessageBody_returnsTheBodyAndItsMimeType_withNoNote() {
        GmailMessageBody expected = messageBody("The quarterly numbers are attached.");

        when(gmailMessageService.getMessageBody("m1")).thenReturn(expected);

        GmailMessageBodyResponse result = new GmailTools(gmailMessageService)
                .getGmailMessageBody(mcpSyncRequestContext, "m1", null);

        assertNull(result.note());
        assertEquals(expected, result.message());
        assertEquals("text/plain", result.message().mimeType());
    }

    @Test
    void getGmailMessageBody_returnsHtmlMarkupVerbatim_withItsMimeType() {
        String markup = "<html><body><p>Quarterly&nbsp;update</p></body></html>";

        GmailMessageBody htmlBody = new GmailMessageBody("m1", "Budget review", "ada@example.com",
                "Mon, 18 Aug 2026 09:00:00 -0500", "text/html", markup);

        when(gmailMessageService.getMessageBody("m1")).thenReturn(htmlBody);

        GmailMessageBodyResponse result = new GmailTools(gmailMessageService)
                .getGmailMessageBody(mcpSyncRequestContext, "m1", null);

        assertNull(result.note());
        assertEquals("text/html", result.message().mimeType());
        assertEquals(markup, result.message().body());
    }

    @Test
    void getGmailMessageBody_truncatesALongBody_andSaysSoInTheNote() {
        when(gmailMessageService.getMessageBody("m1")).thenReturn(messageBody("x".repeat(2_500)));

        GmailMessageBodyResponse result = new GmailTools(gmailMessageService)
                .getGmailMessageBody(mcpSyncRequestContext, "m1", 1_000);

        assertEquals(1_000, result.message().body().length());
        assertNotNull(result.note());
        assertTrue(result.note().contains("truncated"), result.note());
    }

    @Test
    void getGmailMessageBody_leavesABodyShorterThanTheLimitAlone() {
        when(gmailMessageService.getMessageBody("m1")).thenReturn(messageBody("x".repeat(1_200)));

        GmailMessageBodyResponse result = new GmailTools(gmailMessageService)
                .getGmailMessageBody(mcpSyncRequestContext, "m1", null);

        assertNull(result.note());
        assertEquals(1_200, result.message().body().length());
    }

    @Test
    void getGmailMessageBody_reportsAMessageWithNoTextPart_asANote() {
        GmailMessageBody withoutText = new GmailMessageBody("m1", "Budget review", "ada@example.com",
                "Mon, 18 Aug 2026 09:00:00 -0500", null, null);

        when(gmailMessageService.getMessageBody("m1")).thenReturn(withoutText);

        GmailMessageBodyResponse result = new GmailTools(gmailMessageService)
                .getGmailMessageBody(mcpSyncRequestContext, "m1", null);

        assertEquals("Message 'm1' has no readable text body.", result.note());
        assertNull(result.message());
    }

    @Test
    void getGmailMessageBody_reportsAMissingMessage_ratherThanFailing() {
        when(gmailMessageService.getMessageBody("nope"))
                .thenThrow(new GmailMessageNotFoundException("No Gmail message found with id 'nope'"));

        GmailMessageBodyResponse result = new GmailTools(gmailMessageService)
                .getGmailMessageBody(mcpSyncRequestContext, "nope", null);

        assertEquals("No Gmail message found with id 'nope'.", result.note());
        assertNull(result.message());
    }

    @Test
    void getGmailMessageBody_asksTheUserToConnectGoogle_ratherThanFailing() {
        when(gmailMessageService.getMessageBody("m1"))
                .thenThrow(new GoogleReconnectRequiredException("No Google grant"));

        GmailMessageBodyResponse result = new GmailTools(gmailMessageService)
                .getGmailMessageBody(mcpSyncRequestContext, "m1", null);

        assertTrue(result.note().contains("Connect Google in Solesonic settings"), result.note());
        assertNull(result.message());
    }

    /**
     * An emoji straddling the character limit must not be cut in half — an unpaired surrogate is
     * mangled or rejected by a strict JSON encoder.
     */
    @Test
    void getGmailMessageBody_doesNotSplitASurrogatePair_whenTruncating() {
        String bodyWithEmojiOnTheBoundary = "x".repeat(999) + "\uD83C\uDF89" + "x".repeat(500);

        when(gmailMessageService.getMessageBody("m1")).thenReturn(messageBody(bodyWithEmojiOnTheBoundary));

        GmailMessageBodyResponse result = new GmailTools(gmailMessageService)
                .getGmailMessageBody(mcpSyncRequestContext, "m1", 1_000);

        String truncated = result.message().body();

        assertEquals("x".repeat(999), truncated);
        assertFalse(Character.isHighSurrogate(truncated.charAt(truncated.length() - 1)));
    }
}
