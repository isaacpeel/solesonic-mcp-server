package com.solesonic.service.atlassian;

import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.jira.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.service.atlassian.AtlassianConstants.*;

@Service
public class JiraUserService {
    private final Logger log =  LoggerFactory.getLogger(JiraUserService.class);

    private static final String ALL_USERS_QUERY = "";

    @Value("${solesonic.llm.jira.cloud.id.path}")
    private String cloudIdPath;

    @Value("${solesonic.llm.jira.assignee.page-size:100}")
    private int pageSize;

    private final WebClient webClient;

    public JiraUserService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * The first page of assignable users matching {@code userName}.
     */
    public List<User> search(CallerIdentity callerIdentity, String userName) {
        log.info("Searching for user: {}", userName);

        return fetchAssignableUsers(callerIdentity, userName, 0);
    }

    /**
     * Every assignable user on the project, following Jira's pagination until a short page.
     */
    public List<User> listAssignableUsers(CallerIdentity callerIdentity) {
        List<User> assignableUsers = new ArrayList<>();
        Set<String> seenAccountIds = new HashSet<>();
        int startAt = 0;

        while (true) {
            List<User> page = fetchAssignableUsers(callerIdentity, ALL_USERS_QUERY, startAt);

            int newUserCount = 0;

            for (User user : page) {
                if (user != null && seenAccountIds.add(user.accountId())) {
                    assignableUsers.add(user);
                    newUserCount++;
                }
            }

            log.info("Assignable users page at startAt={} returned {} user(s), {} new", startAt, page.size(), newUserCount);

            if (page.size() < pageSize) {
                break;
            }

            if (newUserCount == 0) {
                log.warn("Assignable users page at startAt={} added no new users; stopping pagination", startAt);
                break;
            }

            startAt += page.size();
        }

        log.info("Listed {} assignable user(s) in total", assignableUsers.size());

        return assignableUsers;
    }

    private List<User> fetchAssignableUsers(CallerIdentity callerIdentity, String query, int startAt) {
        String[] basePathSegments = {EX, JIRA, cloudIdPath, REST_PATH, API_PATH, VERSION_PATH, USER_PATH, ASSIGNABLE_PATH, SEARCH_PATH};

        List<User> users = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .queryParam(QUERY_PARAM, query)
                        .queryParam(PROJECT_PARAM, PROJECT_ID)
                        .queryParam(START_AT_PARAM, startAt)
                        .queryParam(MAX_RESULTS_PARAM, pageSize)
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .exchangeToMono(response -> {
                    log.info("Request URI: {}", response.request().getURI());
                    return response.bodyToMono(new ParameterizedTypeReference<List<User>>() {});
                })
                .block();

        return Objects.requireNonNullElse(users, List.of());
    }
}
