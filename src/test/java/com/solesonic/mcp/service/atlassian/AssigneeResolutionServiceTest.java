package com.solesonic.mcp.service.atlassian;

import com.solesonic.agent.model.AssigneeCandidate;
import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.agent.model.AssigneeResolution;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.jira.User;
import com.solesonic.service.atlassian.AssigneeResolutionService;
import com.solesonic.service.atlassian.JiraUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssigneeResolutionServiceTest {

    private static final String USER_REQUEST = "Create a story for the login page and assign it to Bob";
    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    @Mock
    private JiraUserService jiraUserService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private AssigneeResolutionService service;

    @BeforeEach
    void setUp() {
        ByteArrayResource template = new ByteArrayResource("Find the assignee in: {input}".getBytes(StandardCharsets.UTF_8));
        service = new AssigneeResolutionService(jiraUserService, chatClient, template);
    }

    private void stubExtractedSearchTerm(String searchTerm) {
        when(chatClient.prompt(any(Prompt.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(searchTerm);
    }

    private static User user(String accountId, String displayName) {
        return User.accountId(accountId).displayName(displayName).active(true).build();
    }

    @Test
    void resolve_singleMatch_isResolved() {
        stubExtractedSearchTerm("Bob");
        when(jiraUserService.search(CALLER, "Bob")).thenReturn(List.of(user("acc-1", "Bob")));

        AssigneeResolution resolution = service.resolve(CALLER, USER_REQUEST);

        assertThat(resolution).isEqualTo(new AssigneeResolution.Resolved(
                new AssigneeLookupResult(true, "acc-1", "RESOLVED", "Bob")));
    }

    @Test
    void resolve_searchTermIsTrimmedAndUnquotedBeforeSearching() {
        stubExtractedSearchTerm("  \"Bob\"\n");
        when(jiraUserService.search(CALLER, "Bob")).thenReturn(List.of(user("acc-1", "Bob")));

        AssigneeResolution resolution = service.resolve(CALLER, USER_REQUEST);

        assertThat(resolution).isInstanceOf(AssigneeResolution.Resolved.class);
    }

    @Test
    void resolve_severalMatches_isAmbiguousWithEveryMatch() {
        stubExtractedSearchTerm("John");
        when(jiraUserService.search(CALLER, "John")).thenReturn(List.of(
                user("acc-1", "John Smith"),
                user("acc-2", "John Doe")
        ));

        AssigneeResolution resolution = service.resolve(CALLER, USER_REQUEST);

        assertThat(resolution).isEqualTo(new AssigneeResolution.Ambiguous("John", List.of(
                new AssigneeCandidate("acc-1", "John Smith"),
                new AssigneeCandidate("acc-2", "John Doe")
        )));
    }

    @Test
    void resolve_noMatch_isNotFound() {
        stubExtractedSearchTerm("Zed");
        when(jiraUserService.search(CALLER, "Zed")).thenReturn(List.of());

        AssigneeResolution resolution = service.resolve(CALLER, USER_REQUEST);

        assertThat(resolution).isEqualTo(new AssigneeResolution.NotFound("Zed"));
    }

    @Test
    void resolve_jiraReturnsNoBody_isNotFound() {
        stubExtractedSearchTerm("Zed");
        when(jiraUserService.search(CALLER, "Zed")).thenReturn(null);

        AssigneeResolution resolution = service.resolve(CALLER, USER_REQUEST);

        assertThat(resolution).isEqualTo(new AssigneeResolution.NotFound("Zed"));
    }

    @Test
    void resolve_modelReportsNoAssignee_skipsTheSearch() {
        stubExtractedSearchTerm("NONE");

        AssigneeResolution resolution = service.resolve(CALLER, USER_REQUEST);

        assertThat(resolution).isEqualTo(new AssigneeResolution.NotRequested());
        verifyNoInteractions(jiraUserService);
    }

    @Test
    void resolve_modelReturnsBlank_skipsTheSearch() {
        stubExtractedSearchTerm("   ");

        AssigneeResolution resolution = service.resolve(CALLER, USER_REQUEST);

        assertThat(resolution).isEqualTo(new AssigneeResolution.NotRequested());
        verifyNoInteractions(jiraUserService);
    }

    @Test
    void resolve_modelReturnsNothing_skipsTheSearch() {
        stubExtractedSearchTerm(null);

        AssigneeResolution resolution = service.resolve(CALLER, USER_REQUEST);

        assertThat(resolution).isEqualTo(new AssigneeResolution.NotRequested());
        verifyNoInteractions(jiraUserService);
    }

    @Test
    void listAssigneeCandidates_returnsEveryAssignableUser() {
        when(jiraUserService.listAssignableUsers(CALLER)).thenReturn(List.of(
                user("acc-1", "Bob"),
                user("acc-2", "Alice")
        ));

        List<AssigneeCandidate> candidates = service.listAssigneeCandidates(CALLER);

        assertThat(candidates).containsExactly(
                new AssigneeCandidate("acc-1", "Bob"),
                new AssigneeCandidate("acc-2", "Alice")
        );
    }

    @Test
    void listAssigneeCandidates_skipsUsersWithoutAnAccountId() {
        when(jiraUserService.listAssignableUsers(CALLER)).thenReturn(List.of(
                user(null, "Ghost"),
                user("acc-2", "Alice")
        ));

        List<AssigneeCandidate> candidates = service.listAssigneeCandidates(CALLER);

        assertThat(candidates).containsExactly(new AssigneeCandidate("acc-2", "Alice"));
    }

    @Test
    void listAssigneeCandidates_jiraReturnsNoBody_isEmpty() {
        when(jiraUserService.listAssignableUsers(CALLER)).thenReturn(null);

        assertThat(service.listAssigneeCandidates(CALLER)).isEmpty();
    }
}
