package com.solesonic.service.atlassian;

import com.solesonic.agent.model.AssigneeCandidate;
import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.agent.model.AssigneeResolution;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.jira.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.solesonic.agent.config.JiraChatClientConfig.JIRA_ASSIGNEE_CHAT_CLIENT;

@Service
public class AssigneeResolutionService {
    private static final Logger log = LoggerFactory.getLogger(AssigneeResolutionService.class);

    private static final String INPUT = "input";
    private static final String RESOLVED = "RESOLVED";

    /**
     * What {@code jira_assignee_lookup.st} tells the model to return when the request names nobody.
     */
    private static final String NO_ASSIGNEE_SENTINEL = "NONE";

    private static final int LOGGED_REQUEST_MAX_LENGTH = 200;

    private final JiraUserService jiraUserService;
    private final ChatClient chatClient;
    private final Resource jiraAssigneeLookupPrompt;

    public AssigneeResolutionService(JiraUserService jiraUserService,
                                     @Qualifier(JIRA_ASSIGNEE_CHAT_CLIENT) ChatClient chatClient,
                                     @Value("classpath:prompt/jira/jira_assignee_lookup.st") Resource jiraAssigneeLookupPrompt) {
        this.jiraUserService = jiraUserService;
        this.chatClient = chatClient;
        this.jiraAssigneeLookupPrompt = jiraAssigneeLookupPrompt;
    }

    public AssigneeResolution resolve(CallerIdentity callerIdentity, String userRequest) {
        String searchTerm = extractSearchTerm(userRequest);

        if (searchTerm.isEmpty() || NO_ASSIGNEE_SENTINEL.equalsIgnoreCase(searchTerm)) {
            log.warn("No assignee is named in the story request, so no Jira user search was run. The user will be asked to pick one. request=\"{}\"",
                    abbreviate(userRequest));
            return new AssigneeResolution.NotRequested();
        }

        log.info("Assignee search term extracted from the story request: \"{}\"", searchTerm);

        List<User> users = Objects.requireNonNullElse(jiraUserService.search(callerIdentity, searchTerm), List.of());

        log.info("Assignable user search for \"{}\" returned {} user(s)", searchTerm, users.size());

        if (users.isEmpty()) {
            log.warn("No assignable Jira user matches \"{}\". The user will be asked to pick an assignee. request=\"{}\"",
                    searchTerm, abbreviate(userRequest));
            return new AssigneeResolution.NotFound(searchTerm);
        }

        if (users.size() > 1) {
            List<AssigneeCandidate> candidates = toCandidates(users);

            log.warn("Assignee search for \"{}\" is ambiguous: {} users match {}. The user will be asked to choose.",
                    searchTerm, candidates.size(), candidates.stream().map(AssigneeCandidate::label).toList());
            return new AssigneeResolution.Ambiguous(searchTerm, candidates);
        }

        User user = users.getFirst();

        log.info("Resolved assignee \"{}\" to {} ({})", searchTerm, user.displayName(), user.accountId());

        return new AssigneeResolution.Resolved(new AssigneeLookupResult(true, user.accountId(), RESOLVED, user.displayName()));
    }

    /**
     * Every user Jira allows as an assignee on the project, for the assignee picker.
     */
    public List<AssigneeCandidate> listAssigneeCandidates(CallerIdentity callerIdentity) {
        List<User> users = Objects.requireNonNullElse(jiraUserService.listAssignableUsers(callerIdentity), List.of());
        List<AssigneeCandidate> candidates = toCandidates(users);

        if (candidates.isEmpty()) {
            log.warn("Jira returned no assignable users for the project; the assignee picker has nothing to offer");
        } else {
            log.info("Found {} assignable Jira user(s) for the assignee picker", candidates.size());
        }

        return candidates;
    }

    private String extractSearchTerm(String userRequest) {
        PromptTemplate assigneeLookupTemplate = new PromptTemplate(jiraAssigneeLookupPrompt);
        Map<String, Object> inputs = Map.of(INPUT, userRequest);
        Prompt assigneeLookup = assigneeLookupTemplate.create(inputs);

        String content = chatClient.prompt(assigneeLookup).call().content();

        if (content == null) {
            return "";
        }

        return stripQuotes(content.strip());
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).strip();
        }

        return value;
    }

    private static List<AssigneeCandidate> toCandidates(List<User> users) {
        return users.stream()
                .filter(Objects::nonNull)
                .filter(user -> user.accountId() != null && !user.accountId().isBlank())
                .map(AssigneeCandidate::from)
                .toList();
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= LOGGED_REQUEST_MAX_LENGTH) {
            return value;
        }

        return value.substring(0, LOGGED_REQUEST_MAX_LENGTH) + "…";
    }
}
