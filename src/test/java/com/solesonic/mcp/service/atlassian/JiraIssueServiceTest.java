package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.exception.atlassian.DuplicateJiraCreationException;
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
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class JiraIssueServiceTest {
    @Mock
    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    private JiraIssueService service;

    @BeforeEach
    void setUp() {
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        service = new JiraIssueService(webClient, new JsonMapper());
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
    }

    @Test
    void get_shouldReturnIssue_fromApi() {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));

        JiraIssue expected = new JiraIssue.Builder().id("123").key("ISSUE-1").build();

        doReturn(Mono.just(expected)).when(requestHeadersSpec).exchangeToMono(any());

        StepVerifier.create(service.get("ISSUE-1"))
                .assertNext(result -> {
                    assertEquals("123", result.id());
                    assertEquals("ISSUE-1", result.key());
                })
                .verifyComplete();

        // Verify that a URI builder was provided
        verify(requestHeadersUriSpec).uri(any(Function.class));
    }

    @Test
    void create_shouldPostIssue_parseJson_andStoreKey() {
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);

        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(any(Function.class));
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());

        JiraIssue input = new JiraIssue.Builder().id("999").key("TEMP").build();
        String responseJson = "{\"id\":\"123\",\"key\":\"ISSUE-2\"}";

        doReturn(Mono.just(responseJson)).when(requestHeadersSpec).exchangeToMono(any());

        StepVerifier.create(service.create(input))
                .assertNext(created -> {
                    assertEquals("123", created.id());
                    assertEquals("ISSUE-2", created.key());
                })
                .verifyComplete();
    }

    @Test
    void create_shouldThrowDuplicate_whenAlreadyCreatedInThread() {
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);

        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(any(Function.class));
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());

        String responseJson = "{\"id\":\"123\",\"key\":\"ISSUE-3\"}";

        doReturn(Mono.just(responseJson)).when(requestHeadersSpec).exchangeToMono(any());

        StepVerifier.create(service.create(new JiraIssue.Builder().id("1").key("TEMP").build()))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(service.create(new JiraIssue.Builder().id("2").key("TEMP2").build()))
                .expectError(DuplicateJiraCreationException.class)
                .verify();
    }

    @Test
    void create_shouldThrowJiraException_withErrorDetails_whenJiraReturnsErrors() {
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);

        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(any(Function.class));
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());

        String errorJson = "{" +
                "\"errorMessages\":[]," +
                "\"errors\":{\"summary\":\"You must specify a summary of the issue.\"}" +
                "}";

        doReturn(Mono.just(errorJson)).when(requestHeadersSpec).exchangeToMono(any());

        JiraIssue input = new JiraIssue.Builder().id("1").key("TEMP").build();

        StepVerifier.create(service.create(input))
                .expectErrorSatisfies(throwable -> {
                    JiraException ex = (JiraException) throwable;
                    assertTrue(ex.getMessage().contains("Jira issue creation failed"));
                    assertTrue(ex.getMessage().contains("summary: You must specify a summary of the issue."));
                    assertEquals(errorJson, ex.getResponseBody());
                })
                .verify();
    }
}
