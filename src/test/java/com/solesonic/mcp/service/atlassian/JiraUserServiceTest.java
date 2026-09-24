package com.solesonic.mcp.service.atlassian;

import com.solesonic.model.atlassian.jira.User;
import com.solesonic.service.atlassian.JiraUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JiraUserServiceTest {

    private static final int PAGE_SIZE = 2;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Captor
    private ArgumentCaptor<Function<UriBuilder, URI>> uriFunctionCaptor;

    private JiraUserService service;

    @BeforeEach
    void setUp() {
        service = new JiraUserService(webClient);
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
        ReflectionTestUtils.setField(service, "pageSize", PAGE_SIZE);
    }

    private static User user(String accountId) {
        return User.accountId(accountId).displayName("User " + accountId).active(true).timeZone("UTC").accountType("atlassian").build();
    }

    private void stubRequest() {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(uriFunctionCaptor.capture());
    }

    private void stubPages(List<List<User>> pages) {
        stubRequest();

        Object[] laterPages = pages.stream()
                .skip(1)
                .map(Mono::just)
                .toArray();

        doReturn(Mono.just(pages.getFirst()), laterPages).when(requestHeadersSpec).exchangeToMono(any());
    }

    private URI requestedUri(int requestIndex) {
        return uriFunctionCaptor.getAllValues()
                .get(requestIndex)
                .apply(new DefaultUriBuilderFactory("https://api.atlassian.com").builder());
    }

    private MultiValueMap<String, String> queryParameters(int requestIndex) {
        return UriComponentsBuilder.fromUri(requestedUri(requestIndex)).build().getQueryParams();
    }

    @Test
    void search_returnsTheFirstPageOfMatchingUsers() {
        stubPages(List.of(List.of(user("acc-1"), user("acc-2"))));

        List<User> result = service.search("bob");

        assertThat(result).extracting(User::accountId).containsExactly("acc-1", "acc-2");
        assertThat(requestedUri(0).getPath()).endsWith("/cloud-id/rest/api/3/user/assignable/search");
        assertThat(queryParameters(0).getFirst("query")).isEqualTo("bob");
        assertThat(queryParameters(0).getFirst("startAt")).isEqualTo("0");
        assertThat(queryParameters(0).getFirst("maxResults")).isEqualTo(String.valueOf(PAGE_SIZE));
    }

    @Test
    void search_jiraReturnsNoBody_isEmpty() {
        stubRequest();
        doReturn(Mono.empty()).when(requestHeadersSpec).exchangeToMono(any());

        assertThat(service.search("bob")).isEmpty();
    }

    @Test
    void listAssignableUsers_followsPagesUntilAShortPage() {
        stubPages(List.of(
                List.of(user("acc-1"), user("acc-2")),
                List.of(user("acc-3"), user("acc-4")),
                List.of(user("acc-5"))
        ));

        List<User> result = service.listAssignableUsers();

        assertThat(result).extracting(User::accountId).containsExactly("acc-1", "acc-2", "acc-3", "acc-4", "acc-5");
        assertThat(queryParameters(0).getFirst("startAt")).isEqualTo("0");
        assertThat(queryParameters(1).getFirst("startAt")).isEqualTo("2");
        assertThat(queryParameters(2).getFirst("startAt")).isEqualTo("4");
        assertThat(queryParameters(0).getFirst("query")).isEmpty();
    }

    @Test
    void listAssignableUsers_exactMultipleOfThePageSize_stopsOnTheEmptyPage() {
        stubPages(List.of(
                List.of(user("acc-1"), user("acc-2")),
                List.of()
        ));

        List<User> result = service.listAssignableUsers();

        assertThat(result).extracting(User::accountId).containsExactly("acc-1", "acc-2");
        verify(webClient, times(2)).get();
    }

    @Test
    void listAssignableUsers_pageWithNoNewUsers_stopsInsteadOfLooping() {
        stubPages(List.of(
                List.of(user("acc-1"), user("acc-2")),
                List.of(user("acc-1"), user("acc-2"))
        ));

        List<User> result = service.listAssignableUsers();

        assertThat(result).extracting(User::accountId).containsExactly("acc-1", "acc-2");
        verify(webClient, times(2)).get();
    }
}
