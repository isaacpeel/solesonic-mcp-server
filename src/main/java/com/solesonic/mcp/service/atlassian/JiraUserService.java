package com.solesonic.mcp.service.atlassian;

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
import static com.solesonic.mcp.service.atlassian.AtlassianConstants.*;

@Service
public class JiraUserService {
    private final Logger log =  LoggerFactory.getLogger(JiraUserService.class);

    @Value("${solesonic.llm.jira.cloud.id.path}")
    private String cloudIdPath;

    private final WebClient webClient;

    public JiraUserService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    public List<User> search(String userName) {
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
