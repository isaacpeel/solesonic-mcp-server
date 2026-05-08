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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class JiraUserServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    private JiraUserService service;

    @BeforeEach
    void setUp() {
        service = new JiraUserService(webClient);
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
    }

    @Test
    void search_shouldReturnUsers_andBuildExpectedUri() {
        List<User> users = List.of(
                User.accountId("acc-1").displayName("Bob").active(true).timeZone("UTC").accountType("atlassian").build(),
                User.accountId("acc-2").displayName("Alice").active(true).timeZone("UTC").accountType("atlassian").build()
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(users));

        List<User> result = service.search("bob");

        assertEquals(2, result.size());
        assertEquals("acc-1", result.getFirst().accountId());
        assertEquals("Bob", result.getFirst().displayName());

        verify(requestHeadersUriSpec).uri(any(Function.class));
    }
}
