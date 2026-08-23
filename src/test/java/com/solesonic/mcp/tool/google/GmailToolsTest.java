package com.solesonic.mcp.tool.google;

import com.solesonic.mcp.exception.google.GoogleReconnectRequiredException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
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
    void listGmailMessages_formatsEachMessage() {
        when(gmailMessageService.listInboxMessages(DEFAULT_MAX_RESULTS)).thenReturn(List.of(
                new GmailMessageSummary("m1", "Standup notes", "Ada <ada@example.com>", "Mon, 18 Aug 2026"),
                new GmailMessageSummary("m2", "Invoice 42", "billing@example.com", "")));

        String result = new GmailTools(gmailMessageService).listGmailMessages(mcpSyncRequestContext, null);

        assertTrue(result.contains("1. Standup notes — from Ada <ada@example.com> (Mon, 18 Aug 2026)"), result);
        assertTrue(result.contains("2. Invoice 42 — from billing@example.com"), result);
        assertTrue(result.contains("The 2 most recent inbox messages"), result);
    }

    @Test
    void listGmailMessages_reportsAnEmptyInbox() {
        when(gmailMessageService.listInboxMessages(DEFAULT_MAX_RESULTS)).thenReturn(List.of());

        String result = new GmailTools(gmailMessageService).listGmailMessages(mcpSyncRequestContext, null);

        assertEquals("There are no messages in the inbox.", result);
    }

    @Test
    void listGmailMessages_asksTheUserToConnectGoogle_ratherThanFailing() {
        when(gmailMessageService.listInboxMessages(DEFAULT_MAX_RESULTS))
                .thenThrow(new GoogleReconnectRequiredException("No Google grant"));

        String result = new GmailTools(gmailMessageService).listGmailMessages(mcpSyncRequestContext, null);

        assertTrue(result.contains("Connect Google in Solesonic settings"), result);
    }
}
