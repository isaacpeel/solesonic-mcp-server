package com.solesonic.mcp.service.atlassian;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solesonic.mcp.exception.atlassian.DuplicateJiraCreationException;
import com.solesonic.mcp.model.atlassian.jira.JiraIssue;
import com.solesonic.mcp.model.atlassian.jira.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;

@Service
public class JiraService {
    private static final Logger log = LoggerFactory.getLogger(JiraService.class);
    public static final String USER_PATH = "user";
    public static final String ASSIGNABLE_PATH = "assignable";
    public static final String SEARCH_PATH = "search";
    public static final String QUERY_PARAM = "query";
    public static final String PROJECT_PARAM = "project";
    public static final String PROJECT_ID = "10000";
    public static final String ISSUE_TYPE_ID = "10001";

    @Value("${solesonic.llm.jira.cloud.id.path}")
    private String cloudIdPath;

    public static final String EX = "ex";
    public static final String JIRA = "jira";

    private final ThreadLocal<String> jiraIssueHolder = ThreadLocal.withInitial(() -> null);

    public static final String REST_PATH = "rest";
    public static final String API_PATH = "api";
    public static final String VERSION_PATH = "3";

    public static final String ISSUE_PATH = "issue";
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public JiraService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public JiraIssue get(String jiraId) {
        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, ISSUE_PATH, jiraId};

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(JiraIssue.class))
                .block();
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

    public List<User> userSearch(String userName) {
        log.info("Searching for user: {}", userName);
        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, USER_PATH, ASSIGNABLE_PATH, SEARCH_PATH};

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .queryParam(QUERY_PARAM, userName)
                        .queryParam(PROJECT_PARAM, PROJECT_ID)
                        .build())
                .exchangeToMono(response -> {
                    log.info("Request URI: {}", response.request().getURI());
                    return response.bodyToMono(new ParameterizedTypeReference<List<User>>() {});
                })
                .block();

    }
}
