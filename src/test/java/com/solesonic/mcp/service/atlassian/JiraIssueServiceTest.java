package com.solesonic.mcp.service.atlassian;

import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.agent.model.JiraIssueCreatePayload;
import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.jira.JiraIssue;
import com.solesonic.model.atlassian.jira.Transitions;
import com.solesonic.service.atlassian.JiraIssueService;
import com.solesonic.testsupport.RecordingExchangeFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.solesonic.testsupport.RecordingExchangeFunction.callerIdentityOf;
import static org.junit.jupiter.api.Assertions.*;

class JiraIssueServiceTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    private RecordingExchangeFunction backend;
    private JiraIssueService service;

    @BeforeEach
    void setUp() {
        backend = new RecordingExchangeFunction();
        service = new JiraIssueService(backend.webClient(), JsonMapper.builder().build());
        ReflectionTestUtils.setField(service, "cloudIdPath", "cloud-id");
    }

    @Test
    void get_shouldReturnIssue_andCarryTheCaller() {
        backend.respondWithJson("{\"id\":\"123\",\"key\":\"ISSUE-1\"}");

        JiraIssue result = service.get(CALLER, "ISSUE-1");

        assertEquals("123", result.id());
        assertEquals("ISSUE-1", result.key());
        assertEquals(Optional.of(CALLER), callerIdentityOf(backend.onlyRequest()));
        assertTrue(backend.onlyRequest().url().getPath().endsWith("/issue/ISSUE-1"));
    }

    @Test
    void create_shouldPostIssue_parseJson_andCarryTheCaller() {
        backend.respondWithJson("{\"id\":\"123\",\"key\":\"ISSUE-2\"}");

        JiraIssue input = new JiraIssue.Builder().id("999").key("TEMP").build();
        JiraIssue created = service.create(CALLER, input);

        assertEquals("123", created.id());
        assertEquals("ISSUE-2", created.key());
        assertEquals(HttpMethod.POST, backend.onlyRequest().method());
        assertEquals(Optional.of(CALLER), callerIdentityOf(backend.onlyRequest()));
    }

    @Test
    void create_shouldThrowJiraException_withErrorDetails_whenJiraReturnsErrors() {
        String errorJson = "{" +
                "\"errorMessages\":[]," +
                "\"errors\":{\"summary\":\"You must specify a summary of the issue.\"}" +
                "}";
        backend.respondWithJson(errorJson);

        JiraIssue input = new JiraIssue.Builder().id("1").key("TEMP").build();

        JiraException exception = assertThrows(JiraException.class, () -> service.create(CALLER, input));
        assertTrue(exception.getMessage().contains("Jira issue creation failed"));
        assertTrue(exception.getMessage().contains("summary: You must specify a summary of the issue."));
        assertEquals(errorJson, exception.getResponseBody());
    }

    @Test
    void delete_carriesTheCaller() {
        service.delete(CALLER, "ISSUE-3");

        assertEquals(HttpMethod.DELETE, backend.onlyRequest().method());
        assertEquals(Optional.of(CALLER), callerIdentityOf(backend.onlyRequest()));
    }

    @Test
    void getTransitions_carriesTheCaller() {
        backend.respondWithJson("{\"transitions\":[{\"id\":\"31\",\"name\":\"Done\"}]}");

        Transitions transitions = service.getTransitions(CALLER, "ISSUE-4");

        assertEquals("31", transitions.transitions().getFirst().id());
        assertEquals(Optional.of(CALLER), callerIdentityOf(backend.onlyRequest()));
    }

    @Test
    void transitionIssue_carriesTheCaller() {
        service.transitionIssue(CALLER, "ISSUE-5", "31");

        assertEquals(HttpMethod.POST, backend.onlyRequest().method());
        assertEquals(Optional.of(CALLER), callerIdentityOf(backend.onlyRequest()));
    }

    @Test
    void convert_withoutAnAssignee_isRefused() {
        JiraIssueCreatePayload payload = new JiraIssueCreatePayload("Login page", "Build it", List.of("It works"), null);

        JiraException exception = assertThrows(JiraException.class, () -> service.convert(payload));

        assertTrue(exception.getMessage().contains("without an assignee"));
        assertTrue(exception.getMessage().contains("Login page"));
        assertTrue(backend.requests().isEmpty());
    }

    @Test
    void convert_withABlankAssigneeId_isRefused() {
        AssigneeLookupResult blankAssignee = new AssigneeLookupResult(true, " ", "RESOLVED", "Nobody");
        JiraIssueCreatePayload payload = new JiraIssueCreatePayload("Login page", "Build it", List.of("It works"), blankAssignee);

        assertThrows(JiraException.class, () -> service.convert(payload));
    }

    @Test
    void convert_withAnAssignee_setsTheAssigneeAccountId() {
        AssigneeLookupResult assignee = new AssigneeLookupResult(true, "acc-1", "RESOLVED", "Bob");
        JiraIssueCreatePayload payload = new JiraIssueCreatePayload("Login page", "Build it", List.of("It works"), assignee);

        JiraIssue jiraIssue = service.convert(payload);

        assertEquals("acc-1", jiraIssue.fields().assignee().accountId());
        assertEquals("Login page", jiraIssue.fields().summary());
        assertEquals(3, jiraIssue.fields().description().content().size());
    }

    @Test
    void convert_withABlankSummary_isRefused() {
        AssigneeLookupResult assignee = new AssigneeLookupResult(true, "acc-1", "RESOLVED", "Bob");
        JiraIssueCreatePayload payload = new JiraIssueCreatePayload(" ", "Build it", List.of("It works"), assignee);

        JiraException exception = assertThrows(JiraException.class, () -> service.convert(payload));

        assertTrue(exception.getMessage().contains("without a summary"));
        assertTrue(backend.requests().isEmpty());
    }

    @Test
    void convert_withNoAcceptanceCriteria_omitsTheBulletListSection() {
        AssigneeLookupResult assignee = new AssigneeLookupResult(true, "acc-1", "RESOLVED", "Bob");
        JiraIssueCreatePayload payload = new JiraIssueCreatePayload("Login page", "Build it", List.of(), assignee);

        JiraIssue jiraIssue = service.convert(payload);

        assertEquals(1, jiraIssue.fields().description().content().size());
    }
}
