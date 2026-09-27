package com.solesonic.service.atlassian;

import com.solesonic.mcp.exception.atlassian.AtlassianErrorMessages;
import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.mcp.tool.atlassian.JiraIssueTools;
import com.solesonic.agent.model.AssigneeLookupResult;
import com.solesonic.agent.model.JiraIssueCreatePayload;
import com.solesonic.model.atlassian.jira.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.service.atlassian.AtlassianConstants.*;

@Service
public class JiraIssueService {
    private static final Logger log = LoggerFactory.getLogger(JiraIssueService.class);

    public static final String TEXT = "text";
    public static final String PARAGRAPH = "paragraph";
    public static final String BULLET_LIST = "bulletList";
    public static final String DOC = "doc";
    public static final String LIST_ITEM = "listItem";
    public static final String ACCEPTANCE_CRITERIA = "Acceptance Criteria:";

    @Value("${solesonic.llm.jira.cloud.id.path}")
    private String cloudIdPath;

    private final WebClient webClient;
    private final JsonMapper jsonMapper;

    public JiraIssueService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient,
                            JsonMapper jsonMapper) {
        this.webClient = webClient;
        this.jsonMapper = jsonMapper;
    }

    public JiraIssue get(CallerIdentity callerIdentity, String issueId) {
        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH, issueId};

        JiraIssue jiraIssue = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .exchangeToMono(response -> response.bodyToMono(JiraIssue.class))
                .block();

        log.debug("Jira issue successfully retrieved: {}", issueId);

        return jiraIssue;
    }

    public JiraIssue create(CallerIdentity callerIdentity, JiraIssue jiraIssue) {
        log.info("Creating jira issue.");
        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH};

        String jiraIssueJson = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .bodyValue(jiraIssue)
                .exchangeToMono(response -> response.bodyToMono(String.class))
                .block();

        log.info("Jira create response JSON: {}", jiraIssueJson);

        if (StringUtils.isEmpty(jiraIssueJson)) {
            throw new JiraException("Jira issue creation failed: empty response from Jira.", jiraIssueJson);
        }

        // Detect Jira error structure in the JSON body and surface a helpful message to tool callers
        JsonNode root = jsonMapper.readTree(jiraIssueJson);

        Optional<String> errorMessages = AtlassianErrorMessages.extract(root);

        if (errorMessages.isPresent()) {
            throw new JiraException("Jira issue creation failed: " + errorMessages.get(), jiraIssueJson);
        }

        JiraIssue createdJiraIssue = jsonMapper.readValue(jiraIssueJson, JiraIssue.class);
        assert createdJiraIssue != null;

        String issueKey = createdJiraIssue.key();
        assert issueKey != null;
        log.info("Created jira issue with key: {}", issueKey);

        return createdJiraIssue;
    }

    public void delete(CallerIdentity callerIdentity, String issueId) {
        log.info("Deleting jira issue.");

        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH, issueId};

        webClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .exchangeToMono(response -> response.bodyToMono(Void.class))
                .block();

        log.info("Jira issue deleted: {}", issueId);
    }

    public Transitions getTransitions(CallerIdentity callerIdentity, String issueKey) {
        log.info("Fetching available transitions for issue: {}", issueKey);

        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH, issueKey, TRANSITIONS_PATH};

        Transitions transitions = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .exchangeToMono(response -> response.bodyToMono(Transitions.class))
                .block();

        log.info("Transitions retrieved for issue: {}", issueKey);
        return transitions;
    }

    public void transitionIssue(CallerIdentity callerIdentity, String issueKey, String transitionId) {
        log.info("Transitioning issue {} with transition ID: {}", issueKey, transitionId);

        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH, issueKey, TRANSITIONS_PATH};

        Map<String, Map<String, String>> transitionPayload = Map.of(
                "transition", Map.of("id", transitionId)
        );

        webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .bodyValue(transitionPayload)
                .exchangeToMono(response -> response.bodyToMono(Void.class))
                .block();

        log.info("Issue {} transitioned successfully", issueKey);
    }

    /**
     * Builds the Jira create request for a story. Jira rejects issues without an assignee, so a
     * payload without one is refused here rather than sent.
     */
    public JiraIssue convert(JiraIssueCreatePayload jiraIssueCreatePayload) {
        AssigneeLookupResult assigneeLookupResult = jiraIssueCreatePayload.assigneeLookupResult();

        if (assigneeLookupResult == null || assigneeLookupResult.assigneeId() == null || assigneeLookupResult.assigneeId().isBlank()) {
            log.warn("Refusing to build Jira story \"{}\" without an assignee", jiraIssueCreatePayload.summary());
            throw new JiraException("Cannot create Jira story \"%s\" without an assignee; Jira requires one."
                    .formatted(jiraIssueCreatePayload.summary()));
        }

        if (jiraIssueCreatePayload.summary() == null || jiraIssueCreatePayload.summary().isBlank()) {
            log.warn("Refusing to build a Jira story without a summary");
            throw new JiraException("Cannot create a Jira story without a summary; the generated title was empty.");
        }

        JiraIssueTools.CreateJiraRequest createJiraRequest = new JiraIssueTools.CreateJiraRequest(
                jiraIssueCreatePayload.summary(),
                jiraIssueCreatePayload.description(),
                jiraIssueCreatePayload.acceptanceCriteria(),
                assigneeLookupResult.assigneeId());

        TextContent descriptionText = TextContent.text(createJiraRequest.description())
                .type(TEXT)
                .build();

        Content descriptionContent = Content.content(List.of(descriptionText))
                .type(PARAGRAPH)
                .build();

        List<Content> documentContent = new ArrayList<>();
        documentContent.add(descriptionContent);

        List<String> acceptanceCriteriaLines = createJiraRequest.acceptanceCriteria();

        if (acceptanceCriteriaLines != null && !acceptanceCriteriaLines.isEmpty()) {
            List<TextContent> acceptanceCriteria = new ArrayList<>();

            acceptanceCriteriaLines.forEach(ac -> {
                TextContent acceptanceCriteriaItemTextContent = TextContent.type(TEXT)
                        .text(ac)
                        .build();

                TextContent acceptanceCriteriaItemContent = TextContent.type(PARAGRAPH)
                        .content(List.of(acceptanceCriteriaItemTextContent))
                        .build();

                TextContent listItemContent = TextContent.type(LIST_ITEM)
                        .content(List.of(acceptanceCriteriaItemContent))
                        .build();

                acceptanceCriteria.add(listItemContent);
            });

            TextContent acceptanceCriteriaHeader = TextContent.text(ACCEPTANCE_CRITERIA)
                    .type(TEXT)
                    .build();

            Content acceptanceCriteriaContent = Content
                    .content(List.of(acceptanceCriteriaHeader))
                    .type(PARAGRAPH)
                    .build();

            Content bulletList = Content
                    .content(acceptanceCriteria)
                    .type(BULLET_LIST)
                    .build();

            documentContent.add(acceptanceCriteriaContent);
            documentContent.add(bulletList);
        } else {
            // An empty bulletList is invalid Atlassian Document Format, so the section is omitted
            // entirely rather than sent empty.
            log.warn("No acceptance criteria were generated; the Jira description will omit that section");
        }

        Description description = Description.content(documentContent)
                .type(DOC)
                .version(1)
                .build();

        IssueType issueType = IssueType.id(ISSUE_TYPE_ID).build();
        Project project = Project.id(PROJECT_ID).build();
        String assigneeId = createJiraRequest.assigneeId();
        User user = User.accountId(assigneeId).build();

        Fields fields = Fields.summary(createJiraRequest.summary())
                .project(project)
                .description(description)
                .issuetype(issueType)
                .assignee(user)
                .build();

        return JiraIssue.fields(fields).build();
    }


}
