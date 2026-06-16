package com.solesonic.service.atlassian;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.model.atlassian.jira.User;
import com.solesonic.agent.model.AssigneeLookupResult;
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

import static com.solesonic.agent.config.JiraChatClientConfig.USER_STORY_CHAT_CLIENT;

@Service
public class AssigneeResolutionService {
    private static final Logger log = LoggerFactory.getLogger(AssigneeResolutionService.class);

    private static final String INPUT = "input";

    private final JiraUserService jiraUserService;
    private final ChatClient chatClient;

    @Value("classpath:prompt/jira/jira_assignee_lookup.st")
    private Resource jiraAssigneeLookupPrompt;

    public AssigneeResolutionService(JiraUserService jiraUserService,
                                     @Qualifier(USER_STORY_CHAT_CLIENT) ChatClient chatClient) {
        this.jiraUserService = jiraUserService;
        this.chatClient = chatClient;
    }

    public AssigneeLookupResult resolve(String userRequest) {
        log.info("resolve AssigneeResolutionService");

        PromptTemplate assigneeLookupTemplate = new PromptTemplate(jiraAssigneeLookupPrompt);
        Map<String, Object> inputs = Map.of(INPUT, userRequest);
        Prompt assigneeLookup = assigneeLookupTemplate.create(inputs);

        String assigneeToLookup = chatClient.prompt(assigneeLookup).call().content();

        List<User> users = jiraUserService.search(assigneeToLookup);

        User user = users.stream()
                .findFirst()
                .orElseThrow(() -> new JiraException("Assignee lookup failed for required assignee: " + assigneeToLookup));

        log.info("Found assignee: {}", user.displayName());

        return new AssigneeLookupResult(true, user.accountId(), "RESOLVED", user.displayName());
    }
}
