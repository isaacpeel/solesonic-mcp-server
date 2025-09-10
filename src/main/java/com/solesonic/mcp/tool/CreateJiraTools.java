package com.solesonic.mcp.tool;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.model.atlassian.jira.*;
import com.solesonic.mcp.service.atlassian.JiraService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.solesonic.mcp.service.atlassian.JiraService.ISSUE_TYPE_ID;
import static com.solesonic.mcp.service.atlassian.JiraService.PROJECT_ID;
import static com.solesonic.mcp.tool.AssigneeJiraTools.ASSIGN_JIRA;

/**
 * MCP tools service for Jira operations.
 * Provides assignee lookup and issue creation functionality.
 */
@Service
public class CreateJiraTools {
    private static final Logger log = LoggerFactory.getLogger(CreateJiraTools.class);
    public static final String CREATE_JIRA_ISSUE = "create_jira_issue";

    private final JiraService jiraService;

    @Value("${jira.url.template}")
    private String jiraUrlTemplate;

    public CreateJiraTools(JiraService jiraService) {
        this.jiraService = jiraService;
    }

    public record CreateJiraResponse(String issueId, String issueUri) {}
    public record CreateJiraRequest(String summary, String description, List<String> acceptanceCriteria, String assigneeId) {}

    /**
     * Looks up the Jira account ID for a given assignee name/query.
     *
     * @param assignee Name or email of the user to look up
     * @return AssigneeIdLookupResponse with the account ID or null if not found
     */
    @SuppressWarnings("unused")
    @Tool(name = "assignee_id_lookup", description = "Look up Jira accountId for a given assignee string")
    @PreAuthorize("hasAuthority('GROUP_MCP-CREATE-JIRA')")
    public String lookupAssigneeId(String assignee) {
        log.info("Looking up assignee ID for: {}", assignee);

        if (assignee == null || assignee.trim().isEmpty()) {
            log.warn("Empty assignee query provided");
            throw new JiraException("Empty assignee query provided");
        }

        try {
            List<User> users = jiraService.userSearch(assignee);

            if (users == null || users.isEmpty()) {
                log.info("No assignable users found for query: {}", assignee);
                throw new JiraException("No assignable users found for query: " + assignee);
            }

            String accountId = users.getFirst().accountId();
            log.info("Found assignee ID: {} for query: {}", accountId, assignee);
            return accountId;

        } catch (Exception e) {
            log.error("Failed to lookup assignee ID for: {}", assignee, e);
            throw new JiraException("Failed to lookup assignee: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new Jira issue with the provided details.
     * @return CreateJiraIssueResponse with the created issue ID and URL
     */
    @SuppressWarnings("unused")
    @PreAuthorize("hasAuthority('GROUP_MCP-CREATE-JIRA')")
    @Tool(name = CREATE_JIRA_ISSUE, description = "Creates a jira issue.  Use responsibly and ensure no repeated calls for the same request.  If an assignee is needed always call '"+ASSIGN_JIRA+"' first.")
    public CreateJiraResponse createJiraIssue(@ToolParam(description = "Request to create a jira issue.") CreateJiraRequest createJiraRequest) {
        log.debug("Invoking create jira function");
        log.debug("Summary: {}", createJiraRequest.summary);
        log.debug("Description: {}", createJiraRequest.description);
        log.debug("Assignee ID: {}", createJiraRequest.assigneeId);

        TextContent descriptionText = TextContent.text(createJiraRequest.description())
                .type("text")
                .build();

        List<TextContent> acceptanceCriteria = new ArrayList<>();

        createJiraRequest.acceptanceCriteria().forEach(ac -> {
            TextContent acceptanceCriteriaItemTextContent = TextContent.type("text")
                    .text(ac)
                    .build();

            TextContent acceptanceCriteriaItemContent = TextContent.type("paragraph")
                    .content(List.of(acceptanceCriteriaItemTextContent))
                    .build();

            TextContent listItemContent = TextContent.type("listItem")
                    .content(List.of(acceptanceCriteriaItemContent))
                    .build();

            acceptanceCriteria.add(listItemContent);
        });

        Content bulletList = Content
                .content(acceptanceCriteria)
                .type("bulletList")
                .build();

        Content descriptionContent = Content.content(List.of(descriptionText))
                .type("paragraph")
                .build();

        TextContent acceptanceCriteriaHeader = TextContent.text("Acceptance Criteria:")
                .type("text")
                .build();

        Content acceptanceCriteriaContent = Content
                .content(List.of(acceptanceCriteriaHeader))
                .type("paragraph")
                .build();

        Description description = Description.content(List.of(descriptionContent, acceptanceCriteriaContent, bulletList))
                .type("doc")
                .version(1)
                .build();

        IssueType issueType = IssueType.id(ISSUE_TYPE_ID).build();

        Project project = Project.id(PROJECT_ID).build();

        User user = User.accountId(createJiraRequest.assigneeId()).build();

        Fields fields = Fields.summary(createJiraRequest.summary())
                .project(project)
                .description(description)
                .issuetype(issueType)
                .assignee(user)
                .build();

        JiraIssue jiraIssue = JiraIssue.fields(fields).build();

        JiraIssue created = jiraService.create(jiraIssue);

        log.debug("Created jira issue: {}", created);

        String jiraUri = jiraUrlTemplate.replace("{key}", created.key());

        log.debug("Using jira uri: {}", jiraUri);

        return new CreateJiraResponse(created.id(), jiraUri);
    }
}