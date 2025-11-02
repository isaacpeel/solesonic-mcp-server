package com.solesonic.mcp.service.atlassian;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solesonic.mcp.exception.atlassian.DuplicateJiraCreationException;
import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class JiraIssueServiceTest {

    @Mock
    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    private JiraIssueService service;

    @BeforeEach
    void setUp() {
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        service = new JiraIssueService(webClient, new ObjectMapper());
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
    }

    @Test
    void get_shouldReturnIssue_fromApi() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);

        JiraIssue expected = new JiraIssue.Builder().id("123").key("ISSUE-1").build();

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.just(expected));

        JiraIssue result = service.get("ISSUE-1");

        assertNotNull(result);
        assertEquals("123", result.id());
        assertEquals("ISSUE-1", result.key());

        // Verify that a URI builder was provided
        verify(requestHeadersUriSpec).uri(any(Function.class));
    }

    @Test
    void create_shouldPostIssue_parseJson_andStoreKey() {
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);

        JiraIssue input = new JiraIssue.Builder().id("999").key("TEMP").build();
        String responseJson = "{\"id\":\"123\",\"key\":\"ISSUE-2\"}";

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.just(responseJson));

        JiraIssue created = service.create(input);

        assertNotNull(created);
        assertEquals("123", created.id());
        assertEquals("ISSUE-2", created.key());
    }

    @Test
    void create_shouldThrowDuplicate_whenAlreadyCreatedInThread() {
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);

        String responseJson = "{\"id\":\"123\",\"key\":\"ISSUE-3\"}";

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.just(responseJson));

        // First creation sets the thread-local key
        service.create(new JiraIssue.Builder().id("1").key("TEMP").build());

        // Second creation should throw
        assertThrows(DuplicateJiraCreationException.class, () ->
                service.create(new JiraIssue.Builder().id("2").key("TEMP2").build())
        );
    }
}
