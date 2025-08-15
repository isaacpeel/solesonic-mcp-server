package com.solesonic.mcp.jira.client.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.solesonic.mcp.jira.auth.OAuthService;
import com.solesonic.mcp.jira.auth.UserProfileResolver;
import com.solesonic.mcp.jira.client.JiraClient;
import com.solesonic.mcp.jira.config.AtlassianProperties;
import com.solesonic.mcp.jira.error.ToolErrorCode;
import com.solesonic.mcp.jira.error.ToolException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;

/**
 * RestClient-based Jira client with OAuth bearer and single refresh retry on 401.
 */
public class JiraRestClientImpl implements JiraClient {

    private final AtlassianProperties props;
    private final OAuthService oauth;
    private final UserProfileResolver profileResolver;
    private final RestClient.Builder baseBuilder;
    private final MeterRegistry registry;

    public JiraRestClientImpl(AtlassianProperties props, OAuthService oauth, RestClient.Builder builder, MeterRegistry registry) {
        this.props = props;
        this.oauth = oauth;
        this.profileResolver = new UserProfileResolver();
        this.registry = registry;
        String baseUrl = "https://api.atlassian.com/ex/jira/" + props.getCloudId() + "/rest/api/3";
        this.baseBuilder = builder.baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
    }

    private RestClient client() {
        return baseBuilder.build();
    }

    private <T> T withAuthRetry(String endpoint, Invoker<T> invoker) {
        String user = profileResolver.currentProfileId();
        String token = oauth.getValidAccessToken(user).orElse(null);
        if (token == null) throw new ToolException(ToolErrorCode.AUTH_REQUIRED, "Authorization required");
        try {
            T res = invoker.invoke(client(), token);
            registry.counter("mcp.jira.http", "endpoint", endpoint, "status", "success").increment();
            return res;
        } catch (RestClientResponseException restClientResponseException) {
            if (restClientResponseException.getStatusCode().is4xxClientError()) {
                // attempt refresh
                try {
                    oauth.refresh(user);
                    String validToken = oauth.getValidAccessToken(user).orElse(null);

                    if (validToken == null) {
                        throw new ToolException(ToolErrorCode.AUTH_EXPIRED, "Re-auth required");
                    }

                    T response = invoker.invoke(client(), validToken);
                    registry.counter("mcp.jira.http", "endpoint", endpoint, "status", "success").increment();
                    return response;
                } catch (ToolException ex) {
                    registry.counter("mcp.jira.http", "endpoint", endpoint, "status", "401").increment();
                    throw ex;
                } catch (Exception ex) {
                    registry.counter("mcp.jira.http", "endpoint", endpoint, "status", "401").increment();
                    throw new ToolException(ToolErrorCode.AUTH_EXPIRED, "Re-auth required");
                }
            }
            registry.counter("mcp.jira.http", "endpoint", endpoint, "status", String.valueOf(restClientResponseException.getStatusCode().value())).increment();
            throw mapError(restClientResponseException);
        }
    }

    private static ToolException mapError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String msg = e.getResponseBodyAsString();

        return new ToolException(ToolErrorCode.JIRA_ERROR, "Jira error: " + status + (!msg.isBlank() ? " - " + truncate(msg) : ""), status);
    }

    private static String truncate(String string) {
        if (string == null) return null;
        return string.length() <= 300 ? string : string.substring(0, 300);
    }

    @Override
    public CreateIssueResult createIssue(Object requestBody) {
        return withAuthRetry("createIssue", (client, token) -> {
            var resp = client.post()
                    .uri("/issue")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(CreateIssueResponse.class);
            if (resp == null) throw new ToolException(ToolErrorCode.JIRA_ERROR, "Empty response from Jira create issue");
            String issueUri = props.getJiraBaseUrl() + "/browse/" + resp.key;
            return new CreateIssueResult(resp.id, resp.key, issueUri);
        });
    }

    @Override
    public List<AssignableUser> searchAssignableUsers(String query, String projectId) {
        return withAuthRetry("searchAssignableUsers", (client, token) -> {
            var users = client.get()
                    .uri("/user/assignable/search?project={projectId}&query={query}", projectId, query)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(AssignableUserResponse[].class);
            List<AssignableUser> out = new ArrayList<>();
            if (users != null) {
                for (AssignableUserResponse u : users) {
                    out.add(new AssignableUser(u.accountId, u.displayName, u.emailAddress));
                }
            }
            return out;
        });
    }

    @Override
    public Myself getMyself() {
        return withAuthRetry("getMyself", (client, token) -> {
            var me = client.get().uri("/myself")
                    .header("Authorization", "Bearer " + token)
                    .retrieve().body(MyselfResponse.class);
            if (me == null) return null;
            return new Myself(me.accountId, me.emailAddress);
        });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CreateIssueResponse { public String id; public String key; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AssignableUserResponse {
        public String accountId; public String displayName; public String emailAddress;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MyselfResponse { public String accountId; public String emailAddress; }

    private interface Invoker<T> { T invoke(RestClient client, String token); }
}
