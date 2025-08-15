package com.solesonic.mcp.jira.service;

import com.solesonic.mcp.jira.auth.OAuthService;
import com.solesonic.mcp.jira.auth.UserProfileResolver;
import com.solesonic.mcp.jira.client.JiraClient;
import com.solesonic.mcp.jira.config.AtlassianProperties;
import com.solesonic.mcp.jira.error.ToolErrorCode;
import com.solesonic.mcp.jira.error.ToolException;
import com.solesonic.mcp.jira.model.*;
import com.solesonic.mcp.jira.util.HashUtil;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "mcp.jira.enabled", havingValue = "true")
public class JiraTools {

    private final AtlassianProperties props;
    private final OAuthService oauth;
    private final JiraClient jira;
    private final IdempotencyCache cache;
    private final UserProfileResolver resolver;
    private final RateLimiter rateLimiter;
    private final MeterRegistry registry;

    public JiraTools(AtlassianProperties props, OAuthService oauth, JiraClient jira, IdempotencyCache cache, UserProfileResolver resolver, RateLimiter rateLimiter, MeterRegistry registry) {
        this.props = props;
        this.oauth = oauth;
        this.jira = jira;
        this.cache = cache;
        this.resolver = resolver;
        this.rateLimiter = rateLimiter;
        this.registry = registry;
    }

    @SuppressWarnings( "unused")
    @Tool(name = "get_jira_auth_url", description = "Returns a URL for the user to authenticate with Jira via OAuth2 Authorization Code + PKCE")
    public GetJiraAuthUrlResponse getJiraAuthUrl() {
        String user = resolver.currentProfileId();
        enforceRate("get_jira_auth_url", user);
        registry.counter("mcp.tools.invocations", "tool", "get_jira_auth_url").increment();
        OAuthService.AuthUrl authUrl = oauth.getAuthUrl(user);
        return new GetJiraAuthUrlResponse(authUrl.url(), authUrl.state(), authUrl.expiresAt());
    }

    @SuppressWarnings( "unused")
    @Tool(name = "complete_jira_auth", description = "Completes OAuth2 flow using the code and state returned by Atlassian and persists tokens")
    public CompleteJiraAuthResponse completeJiraAuth(CompleteJiraAuthRequest request) {
        String user = resolver.currentProfileId();
        enforceRate("complete_jira_auth", user);
        validateText("state", request.state());
        validateText("code", request.code());
        List<String> scopes = oauth.completeAuth(user, request.state(), request.code());
        return new CompleteJiraAuthResponse(true, scopes);
    }

    @SuppressWarnings( "unused")
    @Tool(name = "test_jira_auth", description = "Performs a lightweight Jira call to verify token and optionally returns account identifier")
    public TestJiraAuthResponse testJiraAuth() {
        String user = resolver.currentProfileId();
        enforceRate("test_jira_auth", user);
        try {
            JiraClient.Myself myself = jira.getMyself();
            if (myself == null) return new TestJiraAuthResponse(false, null, null);
            return new TestJiraAuthResponse(true, myself.accountId(), myself.emailAddress());
        } catch (ToolException ex) {
            if (ex.getCode() == ToolErrorCode.AUTH_REQUIRED || ex.getCode() == ToolErrorCode.AUTH_EXPIRED) {
                return new TestJiraAuthResponse(false, null, null);
            }
            throw ex;
        }
    }

    @SuppressWarnings( "unused")
    @Tool(name = "search_assignable_users", description = "Search Jira assignable users for the configured project")
    public SearchAssignableUsersResponse searchAssignableUsers(SearchAssignableUsersRequest request) {
        String user = resolver.currentProfileId();
        enforceRate("search_assignable_users", user);
        String queryString = request.query() == null ? "" : request.query().trim();
        if (queryString.isEmpty()) {
            throw new ToolException(ToolErrorCode.VALIDATION_ERROR, "query must not be empty");
        }
        var users = jira.searchAssignableUsers(queryString, required(props.getProjectId(), "atlassian.project-id"));
        List<SearchAssignableUsersResponse.User> usersOut = new ArrayList<>();
        for (JiraClient.AssignableUser assignableUser : users) {
            usersOut.add(new SearchAssignableUsersResponse.User(assignableUser.accountId(), assignableUser.displayName(), assignableUser.emailAddress()));
        }
        return new SearchAssignableUsersResponse(usersOut);
    }

    @SuppressWarnings( "unused")
    @Tool(name = "create_jira_issue", description = "Creates a Jira issue using configured project/issueType and acceptance criteria formatting. Supports idempotency.")
    public CreateJiraIssueResponse createJiraIssue(CreateJiraIssueRequest createIssueRequest) {
        String user = resolver.currentProfileId();
        enforceRate("create_jira_issue", user);
        // Validate
        validateText("summary", createIssueRequest.summary());
        validateText("description", createIssueRequest.description());
        List<String> acceptanceCriteria = normalizeAC(createIssueRequest.acceptanceCriteria());
        String assigneeId = Optional.ofNullable(createIssueRequest.assigneeId()).map(String::trim).orElse("");

        // Idempotency hash
        String normalized = String.join("|",
                createIssueRequest.summary().trim(),
                createIssueRequest.description().trim(),
                String.join("\n", acceptanceCriteria),
                assigneeId,
                required(props.getProjectId(), "atlassian.project-id"),
                required(props.getIssueTypeId(), "atlassian.issue-type-id")
        );
        String hash = HashUtil.sha256Hex(normalized);

        boolean force = Boolean.TRUE.equals(createIssueRequest.forceCreate());
        if (!force) {
            var cachedIssue = cache.get(user, hash);

            if (cachedIssue.isPresent()) {
                var jiraIssue = cachedIssue.get();
                return new CreateJiraIssueResponse(jiraIssue.issueId(), jiraIssue.issueKey(), jiraIssue.issueUri(), jiraIssue.issueKey());
            }
        }

        // Ensure auth exists early for better UX
        if (oauth.getValidAccessToken(user).isEmpty()) {
            throw new ToolException(ToolErrorCode.AUTH_REQUIRED, "Authorization required");
        }

        // Build Jira document description
        Object descriptionDoc = buildDescriptionDoc(createIssueRequest.description().trim(), acceptanceCriteria);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("summary", createIssueRequest.summary().trim());
        Map<String, Object> project = Map.of("id", required(props.getProjectId(), "atlassian.project-id"));
        fields.put("project", project);
        Map<String, Object> issueType = Map.of("id", required(props.getIssueTypeId(), "atlassian.issue-type-id"));
        fields.put("issuetype", issueType);
        fields.put("description", descriptionDoc);

        if (!assigneeId.isEmpty()) {
            fields.put("assignee", Map.of("accountId", assigneeId));
        }

        Map<String, Object> body = Map.of("fields", fields);

        JiraClient.CreateIssueResult result = jira.createIssue(body);
        CreateJiraIssueResponse response = new CreateJiraIssueResponse(result.issueId(), result.issueKey(), result.issueUri(), null);
        cache.put(user, hash, response);
        return response;
    }

    private static void validateText(String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ToolException(ToolErrorCode.VALIDATION_ERROR, field + " must not be empty");
        }
    }

    private static List<String> normalizeAC(List<String> list) {
        if (list == null) {
            return List.of();
        }

        List<String> trimmedList = new ArrayList<>();
        for (Object element : list) {
            if (element == null) {
                continue;
            }
            String trimmed = element.toString().trim();
            if (!trimmed.isEmpty()) {
                trimmedList.add(trimmed);
            }
        }
        return trimmedList;
    }

    private static Map<String, Object> buildDescriptionDoc(String description, List<String> acceptanceCriteria) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("type", "doc");
        document.put("version", 1);
        List<Object> content = new ArrayList<>();
        // description paragraph
        content.add(paragraph(description));
        // Acceptance Criteria header paragraph
        content.add(paragraph("Acceptance Criteria:"));
        // bullet list
        Map<String, Object> bulletList = new LinkedHashMap<>();
        bulletList.put("type", "bulletList");
        List<Object> blContent = new ArrayList<>();

        for (String item : acceptanceCriteria) {
            Map<String, Object> listItem = new LinkedHashMap<>();
            listItem.put("type", "listItem");
            listItem.put("content", List.of(paragraph(item)));
            blContent.add(listItem);
        }

        bulletList.put("content", blContent);
        content.add(bulletList);
        document.put("content", content);
        return document;
    }

    private static Map<String, Object> paragraph(String text) {
        Map<String, Object> contentMap = new LinkedHashMap<>();
        contentMap.put("type", "paragraph");
        contentMap.put("content", List.of(textNode(text)));
        return contentMap;
    }

    private static Map<String, Object> textNode(String t) {
        Map<String, Object> textNode = new LinkedHashMap<>();
        textNode.put("type", "text");
        textNode.put("text", t);
        return textNode;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ToolException(ToolErrorCode.VALIDATION_ERROR, "Missing property: " + name);
        }

        return value;
    }

    private void enforceRate(String tool, String userProfileId) {
        if (rateLimiter == null) {
            return;
        }

        String key = userProfileId + ":" + tool;

        if (!rateLimiter.allow(key)) {
            throw new ToolException(ToolErrorCode.VALIDATION_ERROR, "Rate limit exceeded for tool: " + tool + "; try again later");
        }
    }
}
