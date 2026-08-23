package com.solesonic.mcp.tool.google;

import com.solesonic.mcp.exception.google.GmailLabelNotFoundException;
import com.solesonic.mcp.exception.google.GoogleReconnectRequiredException;
import com.solesonic.model.google.gmail.GmailMessageListResponse;
import com.solesonic.model.google.gmail.GmailMessageSummary;
import com.solesonic.service.google.GmailMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import java.util.List;

import static com.solesonic.mcp.tool.google.GmailTools.DEFAULT_MAX_RESULTS;
import static com.solesonic.mcp.tool.google.GmailTools.MAXIMUM_MAX_RESULTS;
import static com.solesonic.mcp.tool.google.GmailTools.MINIMUM_MAX_RESULTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
