package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.jira.User;
import com.solesonic.service.atlassian.JiraUserService;
import com.solesonic.testsupport.RecordingExchangeFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static com.solesonic.testsupport.RecordingExchangeFunction.callerIdentityOf;
import static org.assertj.core.api.Assertions.assertThat;

class JiraUserServiceTest {

    private static final int PAGE_SIZE = 2;
    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private RecordingExchangeFunction backend;
    private JiraUserService service;

    @BeforeEach
    void setUp() {
        backend = new RecordingExchangeFunction();
        service = new JiraUserService(backend.webClient());
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
        ReflectionTestUtils.setField(service, "pageSize", PAGE_SIZE);
    }

    private static User user(String accountId) {
        return User.accountId(accountId).displayName("User " + accountId).active(true).timeZone("UTC").accountType("atlassian").build();
    }

    private void respondWithPages(List<List<User>> pages) {
        for (List<User> page : pages) {
            backend.respondWithJson(jsonMapper.writeValueAsString(page));
        }
    }

    private MultiValueMap<String, String> queryParameters(int requestIndex) {
        return UriComponentsBuilder.fromUri(backend.requests().get(requestIndex).url()).build().getQueryParams();
    }

    @Test
    void search_returnsTheFirstPageOfMatchingUsers() {
        respondWithPages(List.of(List.of(user("acc-1"), user("acc-2"))));

        List<User> result = service.search(CALLER, "bob");

        assertThat(result).extracting(User::accountId).containsExactly("acc-1", "acc-2");
        assertThat(backend.onlyRequest().url().getPath()).endsWith("/cloud-id/rest/api/3/user/assignable/search");
        assertThat(queryParameters(0).getFirst("query")).isEqualTo("bob");
        assertThat(queryParameters(0).getFirst("startAt")).isEqualTo("0");
        assertThat(queryParameters(0).getFirst("maxResults")).isEqualTo(String.valueOf(PAGE_SIZE));
        assertThat(callerIdentityOf(backend.onlyRequest())).contains(CALLER);
    }

    @Test
    void search_jiraReturnsNoBody_isEmpty() {
        backend.respondWithStatus(HttpStatus.OK);

        assertThat(service.search(CALLER, "bob")).isEmpty();
    }

    @Test
    void listAssignableUsers_followsPagesUntilAShortPage_carryingTheCallerOnEveryPage() {
        respondWithPages(List.of(
                List.of(user("acc-1"), user("acc-2")),
                List.of(user("acc-3"), user("acc-4")),
                List.of(user("acc-5"))
        ));

        List<User> result = service.listAssignableUsers(CALLER);

        assertThat(result).extracting(User::accountId).containsExactly("acc-1", "acc-2", "acc-3", "acc-4", "acc-5");
        assertThat(queryParameters(0).getFirst("startAt")).isEqualTo("0");
        assertThat(queryParameters(1).getFirst("startAt")).isEqualTo("2");
        assertThat(queryParameters(2).getFirst("startAt")).isEqualTo("4");
        assertThat(queryParameters(0).getFirst("query")).isEmpty();
        assertThat(backend.requests()).allSatisfy(request -> assertThat(callerIdentityOf(request)).contains(CALLER));
    }

    @Test
    void listAssignableUsers_exactMultipleOfThePageSize_stopsOnTheEmptyPage() {
        respondWithPages(List.of(
                List.of(user("acc-1"), user("acc-2")),
                List.of()
        ));

        List<User> result = service.listAssignableUsers(CALLER);

        assertThat(result).extracting(User::accountId).containsExactly("acc-1", "acc-2");
        assertThat(backend.requests()).hasSize(2);
    }

    @Test
    void listAssignableUsers_pageWithNoNewUsers_stopsInsteadOfLooping() {
        respondWithPages(List.of(
                List.of(user("acc-1"), user("acc-2")),
                List.of(user("acc-1"), user("acc-2"))
        ));

        List<User> result = service.listAssignableUsers(CALLER);

        assertThat(result).extracting(User::accountId).containsExactly("acc-1", "acc-2");
        assertThat(backend.requests()).hasSize(2);
    }
}
