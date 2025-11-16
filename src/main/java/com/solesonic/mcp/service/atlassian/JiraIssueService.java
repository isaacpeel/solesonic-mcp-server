package com.solesonic.mcp.service.atlassian;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solesonic.mcp.exception.atlassian.DuplicateJiraCreationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.mcp.service.atlassian.AtlassianConstants.*;

@Service
public class JiraIssueService {
    private static final Logger log = LoggerFactory.getLogger(JiraIssueService.class);

    @Value("${solesonic.llm.jira.cloud.id.path}")
    private String cloudIdPath;

    private final ThreadLocal<String> jiraIssueHolder = ThreadLocal.withInitial(() -> null);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public JiraIssueService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public JiraIssue get(String issueId) {
        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH, issueId};

        JiraIssue jiraIssue = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(JiraIssue.class))
                .block();

        log.info("Jira issue successfully retrieved: {}", issueId);

        return jiraIssue;
    }

    public JiraIssue create(JiraIssue jiraIssue) {
        log.info("Creating jira issue.");

        if (jiraIssueHolder.get() != null) {
            throw new DuplicateJiraCreationException(jiraIssueHolder.get());
        }

        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH};

        String jiraIssueJson = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .bodyValue(jiraIssue)
                .exchangeToMono(response -> response.bodyToMono(String.class))
                .block();

        log.info("Jira create response JSON: {}", jiraIssueJson);

        if (jiraIssueJson == null || jiraIssueJson.isBlank()) {
            throw new JiraException("Jira issue creation failed: empty response from Jira.", jiraIssueJson);
        }

        // Detect Jira error structure in the JSON body and surface a helpful message to tool callers
        try {
            JsonNode root = objectMapper.readTree(jiraIssueJson);

            boolean hasErrorMessages = root.has("errorMessages") && root.get("errorMessages").isArray() && !root.get("errorMessages").isEmpty();
            boolean hasErrorsObject = root.has("errors") && root.get("errors").isObject() && !root.get("errors").isEmpty();

            if (hasErrorMessages || hasErrorsObject) {
                StringBuilder messageBuilder = new StringBuilder("Jira issue creation failed: ");

                if (hasErrorMessages) {
                    for (JsonNode msgNode : root.get("errorMessages")) {
                        if (messageBuilder.length() > 30) { // already has some content
                            messageBuilder.append("; ");
                        }
                        messageBuilder.append(msgNode.asText());
                    }
                }

                if (hasErrorsObject) {
                    root.get("errors").properties().forEach(entry -> {
                        if (messageBuilder.length() > 30) {
                            messageBuilder.append("; ");
                        }
                        messageBuilder.append(entry.getKey()).append(": ").append(entry.getValue().asText());
                    });
                }

                throw new JiraException(messageBuilder.toString(), jiraIssueJson);
            }
        } catch (JsonProcessingException e) {
            // If we cannot parse for errors, proceed to try mapping to a JiraIssue below.
            log.debug("Unable to parse Jira response for error details.", e);
        }

        try {
            jiraIssue = objectMapper.readValue(jiraIssueJson, JiraIssue.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        assert jiraIssue != null;

        String issueKey = jiraIssue.key();
        assert issueKey != null;

        log.info("Created jira issue with key: {}", issueKey);
        jiraIssueHolder.set(issueKey);

        return jiraIssue;
    }

    public void delete(String issueId) {
        log.info("Deleting jira issue.");

        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH, issueId};

        webClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(JiraIssue.class))
                .block();
    }
}
