package com.solesonic.mcp.workflow.service;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.model.atlassian.jira.User;
import com.solesonic.mcp.service.atlassian.JiraUserService;
import com.solesonic.mcp.workflow.WeightedProgressCoordinator;
import com.solesonic.mcp.workflow.model.AssigneeLookupResult;
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

import static com.solesonic.mcp.workflow.chain.UserStoryChainConfig.USER_STORY_CHAT_CLIENT;

@Service
public class AssigneeResolutionService {
    private static final Logger log = LoggerFactory.getLogger(AssigneeResolutionService.class);

    private static final String INPUT = "input";

    private final JiraUserService jiraUserService;
    private final ChatClient chatClient;

    @Value("classpath:prompt/jira_assignee_lookup.st")
    private Resource jiraAssigneeLookupPrompt;

    public AssigneeResolutionService(JiraUserService jiraUserService,
                                     @Qualifier(USER_STORY_CHAT_CLIENT) ChatClient chatClient) {
        this.jiraUserService = jiraUserService;
        this.chatClient = chatClient;
    }

    public AssigneeLookupResult resolve(String userRequest, WeightedProgressCoordinator.TaskProgress taskProgress) {
        log.info("resolve AssigneeResolutionService");
        taskProgress.update(0.20, "Looking up assignee");

        PromptTemplate assigneeLookupTemplate = new PromptTemplate(jiraAssigneeLookupPrompt);
        Map<String, Object> inputs = Map.of(INPUT, userRequest);
        Prompt assigneeLookup = assigneeLookupTemplate.create(inputs);

        String assigneeToLookup = chatClient.prompt(assigneeLookup).call().content();
        taskProgress.update(0.55, "Searching assignable users");

        List<User> users = jiraUserService.search(assigneeToLookup);

        User user = users.stream()
                .findFirst()
                .orElseThrow(() -> new JiraException("Assignee lookup failed for required assignee: " + assigneeToLookup));

        log.info("Found assignee: {}", user.displayName());
        taskProgress.done("Found assignee: " + user.displayName());

        return new AssigneeLookupResult(true, user.accountId(), "RESOLVED", user.displayName());
    }
}
