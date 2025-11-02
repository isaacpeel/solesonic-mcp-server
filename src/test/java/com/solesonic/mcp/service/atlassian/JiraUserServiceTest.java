package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.model.atlassian.jira.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class JiraUserServiceTest {

    @Mock
    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    private JiraUserService service;

    @BeforeEach

    void setUp() {
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        service = new JiraUserService(webClient);
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
    }

    @Test
    void search_shouldReturnUsers_andBuildExpectedUri() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);

        List<User> users = List.of(
                User.accountId("acc-1").displayName("Bob").active(true).timeZone("UTC").accountType("atlassian").build(),
                User.accountId("acc-2").displayName("Alice").active(true).timeZone("UTC").accountType("atlassian").build()
        );

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.just(users));

        List<User> result = service.search("bob");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("acc-1", result.getFirst().accountId());
        assertEquals("Bob", result.getFirst().displayName());

        // Verify that uri builder function was provided
        verify(requestHeadersUriSpec).uri(any(Function.class));
    }
}
