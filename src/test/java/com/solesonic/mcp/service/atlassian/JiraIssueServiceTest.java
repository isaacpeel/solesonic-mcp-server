package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class JiraIssueServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    private JiraIssueService service;

    @BeforeEach
    void setUp() {
        service = new JiraIssueService(webClient, new JsonMapper());
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
    }

    @Test
    void get_shouldReturnIssue_fromApi() {
        JiraIssue expected = new JiraIssue.Builder().id("123").key("ISSUE-1").build();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(expected));

        JiraIssue result = service.get("ISSUE-1");

        assertEquals("123", result.id());
        assertEquals("ISSUE-1", result.key());
        verify(requestHeadersUriSpec).uri(any(Function.class));
    }

    @Test
    void create_shouldPostIssue_parseJson_andStoreKey() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(any(Function.class));
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());

        String responseJson = "{\"id\":\"123\",\"key\":\"ISSUE-2\"}";
        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(responseJson));

        JiraIssue input = new JiraIssue.Builder().id("999").key("TEMP").build();
        JiraIssue created = service.create(input);

        assertEquals("123", created.id());
        assertEquals("ISSUE-2", created.key());
    }

    @Test
    void create_shouldThrowJiraException_withErrorDetails_whenJiraReturnsErrors() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(any(Function.class));
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());

        String errorJson = "{" +
                "\"errorMessages\":[]," +
                "\"errors\":{\"summary\":\"You must specify a summary of the issue.\"}" +
                "}";
        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(errorJson));

        JiraIssue input = new JiraIssue.Builder().id("1").key("TEMP").build();

        JiraException exception = assertThrows(JiraException.class, () -> service.create(input));
        assertTrue(exception.getMessage().contains("Jira issue creation failed"));
        assertTrue(exception.getMessage().contains("summary: You must specify a summary of the issue."));
        assertEquals(errorJson, exception.getResponseBody());
    }
}
