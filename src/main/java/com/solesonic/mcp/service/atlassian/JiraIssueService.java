package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.mcp.service.atlassian.AtlassianConstants.*;

@Service
public class JiraIssueService {
    private static final Logger log = LoggerFactory.getLogger(JiraIssueService.class);

    @Value("${solesonic.llm.jira.cloud.id.path}")
    private String cloudIdPath;

    private final WebClient webClient;
    private final JsonMapper jsonMapper;

    public JiraIssueService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient, JsonMapper jsonMapper) {
        this.webClient = webClient;
        this.jsonMapper = jsonMapper;
    }

    public Mono<JiraIssue> get(String issueId) {
        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH, issueId};

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(JiraIssue.class))
                .doOnSuccess(jiraIssue -> log.info("Jira issue successfully retrieved: {}", issueId));
    }

    public Mono<JiraIssue> create(JiraIssue jiraIssue) {
        log.info("Creating jira issue.");
        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH};

        return Mono.defer(() -> webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .bodyValue(jiraIssue)
                .exchangeToMono(response -> response.bodyToMono(String.class)))
                .flatMap(jiraIssueJson -> {
                    log.info("Jira create response JSON: {}", jiraIssueJson);

                    if (StringUtils.isEmpty(jiraIssueJson)) {
                        return Mono.error(new JiraException("Jira issue creation failed: empty response from Jira.", jiraIssueJson));
                    }

                    JsonNode root = jsonMapper.readTree(jiraIssueJson);
                    boolean hasErrorMessages = root.has("errorMessages") && root.get("errorMessages").isArray() && !root.get("errorMessages").isEmpty();
                    boolean hasErrorsObject = root.has("errors") && root.get("errors").isObject() && !root.get("errors").isEmpty();

                    if (hasErrorMessages || hasErrorsObject) {
                        StringBuilder messageBuilder = new StringBuilder("Jira issue creation failed: ");

                        if (hasErrorMessages) {

                            for (JsonNode msgNode : root.get("errorMessages")) {
                                if (messageBuilder.length() > 30) {
                                    messageBuilder.append("; ");
                                }

                                messageBuilder.append(msgNode.asString());
                            }
                        }

                        if (hasErrorsObject) {
                            root.get("errors").properties().forEach(entry -> {
                                if (messageBuilder.length() > 30) {
                                    messageBuilder.append("; ");
                                }

                                messageBuilder.append(entry.getKey()).append(": ").append(entry.getValue().asString());
                            });
                        }

                        return Mono.error(new JiraException(messageBuilder.toString(), jiraIssueJson));
                    }

                    JiraIssue createdJiraIssue = jsonMapper.readValue(jiraIssueJson, JiraIssue.class);
                    assert createdJiraIssue != null;

                    String issueKey = createdJiraIssue.key();
                    assert issueKey != null;
                    log.info("Created jira issue with key: {}", issueKey);

                    return Mono.just(createdJiraIssue);
                });
    }

    public Mono<Void> delete(String issueId) {
        log.info("Deleting jira issue.");

        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH, issueId};

        return webClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(JiraIssue.class))
                .then();
    }
}
