package com.solesonic.mcp.service.atlassian;


import com.solesonic.mcp.model.atlassian.auth.AtlassianAccessToken;
import com.solesonic.mcp.repository.AtlassianAccessTokenRepository;
import com.solesonic.mcp.scope.UserRequestContext;
import com.solesonic.mcp.security.atlassian.AtlassianAuthRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.time.ZonedDateTime;
import java.util.UUID;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_AUTH_WEB_CLIENT;
import static java.nio.charset.StandardCharsets.UTF_8;

@Service
public class JiraAuthService {
    private static final Logger log = LoggerFactory.getLogger(JiraAuthService.class);
    public static final String AUTHORIZATION_CODE = "authorization_code";
    public static final String AUDIENCE = "api.atlassian.com";
    public static final String REFRESH_TOKEN = "refresh_token";

    public static final String[] SCOPES = {
            //Granular scopes
            URLEncoder.encode("read:issue:jira", UTF_8),
            URLEncoder.encode("write:issue:jira", UTF_8),
            URLEncoder.encode("read:issue:jira-software", UTF_8),
            URLEncoder.encode("delete:issue:jira", UTF_8),
            URLEncoder.encode("read:avatar:jira", UTF_8),
            URLEncoder.encode("write:issue:jira-software", UTF_8),
            URLEncoder.encode("read:issue-meta:jira", UTF_8),
            URLEncoder.encode("read:field-configuration:jira", UTF_8),
            URLEncoder.encode("read:issue-security-level:jira", UTF_8),
            URLEncoder.encode("read:issue.changelog:jira", UTF_8),
            URLEncoder.encode("read:issue.vote:jira", UTF_8),
            URLEncoder.encode("read:user:jira", UTF_8),
            URLEncoder.encode("read:status:jira", UTF_8),
            URLEncoder.encode("read:application-role:jira", UTF_8),
            URLEncoder.encode("read:group:jira", UTF_8),
            URLEncoder.encode("write:page:confluence", UTF_8),
            URLEncoder.encode("read:page:confluence", UTF_8),
            URLEncoder.encode("delete:page:confluence", UTF_8),
            URLEncoder.encode("read:space:confluence", UTF_8),
            URLEncoder.encode("write:space:confluence", UTF_8),
            URLEncoder.encode("read:space-details:confluence", UTF_8),

            //Special
            URLEncoder.encode("READ", UTF_8),
            URLEncoder.encode("WRITE", UTF_8),
            URLEncoder.encode("offline_access", UTF_8),

            //Classic Scopes
            URLEncoder.encode("read:jira-work", UTF_8),
            URLEncoder.encode("manage:jira-project", UTF_8),
            URLEncoder.encode("manage:jira-configuration", UTF_8),
            URLEncoder.encode("read:jira-user", UTF_8),
            URLEncoder.encode("write:jira-work", UTF_8),
            URLEncoder.encode("manage:jira-webhook", UTF_8),
            URLEncoder.encode("manage:jira-data-provider", UTF_8),
            URLEncoder.encode("read:servicedesk-request", UTF_8),
            URLEncoder.encode("manage:servicedesk-customer", UTF_8),
            URLEncoder.encode("write:servicedesk-request", UTF_8),
            URLEncoder.encode("read:servicemanagement-insight-objects", UTF_8)
    };

    public static final String RESPONSE_TYPE = "code";
    public static final String PROMPT = "consent";
    public static final String OAUTH_PATH = "oauth";
    public static final String TOKEN_PATH = "token";
    public static final String ACCESSIBLE_RESOURCES_PATH = "accessible-resource";
    public static final String AUTHORIZE_PATH = "authorize";
    public static final String AUDIENCE_PARAM = "audience";
    public static final String CLIENT_ID_PARAM = "client_id";
    public static final String SCOPE_PARAM = "scope";
    public static final String REDIRECT_URI_PARAM = "redirect_uri";
    public static final String STATE_PARAM = "state";
    public static final String RESPONSE_TYPE_PARAM = "response_type";
    public static final String PROMPT_PARAM = "prompt";

    @Value("${jira.api.auth.uri:na}")
    private String jiraAuthUri;

    @Value("${jira.api.auth.callback.uri:na}")
    private String authCallbackUri;

    @Value("${jira.api.client.id:na}")
    private String authClientId;

    @Value("${jira.api.client.secret:na}")
    private String authClientSecret;

    @Value("${jira.api.client.id:na}")
    private String clientId;

    private final UserRequestContext userRequestContext;
    private final AtlassianAccessTokenRepository atlassianAccessTokenRepository;
    private final WebClient webClient;
    private final WebClient apiWebClient;

    public JiraAuthService(UserRequestContext userRequestContext, AtlassianAccessTokenRepository atlassianAccessTokenRepository,
                           @Qualifier(ATLASSIAN_AUTH_WEB_CLIENT) WebClient webClient,
                           @Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient apiWebClient) {
        this.userRequestContext = userRequestContext;
        this.atlassianAccessTokenRepository = atlassianAccessTokenRepository;
        this.webClient = webClient;
        this.apiWebClient = apiWebClient;
    }

    public String authUri() {
        UUID userId = userRequestContext.getUserId();
        log.info("Building auth URI for user: {}", userId);

        return UriComponentsBuilder.fromUriString(jiraAuthUri)
                .pathSegment(AUTHORIZE_PATH)
                .queryParam(AUDIENCE_PARAM, AUDIENCE)
                .queryParam(CLIENT_ID_PARAM, clientId)
                .queryParam(SCOPE_PARAM, String.join(" ", SCOPES))
                .queryParam(REDIRECT_URI_PARAM, URLEncoder.encode(authCallbackUri, UTF_8))
                .queryParam(STATE_PARAM, userId)
                .queryParam(RESPONSE_TYPE_PARAM, RESPONSE_TYPE)
                .queryParam(PROMPT_PARAM, PROMPT)
                .build()
                .toUriString();
    }

    public void callback(String code) {
        UUID userId = userRequestContext.getUserId();
        log.info("Callback for user: {}", userId);

        AtlassianAuthRequest atlassianAuthRequest = AtlassianAuthRequest.builder()
                .grantType(AUTHORIZATION_CODE)
                .clientId(authClientId)
                .clientSecret(authClientSecret)
                .code(code)
                .redirectUri(authCallbackUri)
                .build();

        AtlassianAccessToken atlassianAccessToken = webClient.post()
                .uri(uriBuilder ->
                        uriBuilder
                                .pathSegment(OAUTH_PATH)
                                .pathSegment(TOKEN_PATH)
                                .build()
                )
                .bodyValue(atlassianAuthRequest)
                .exchangeToMono(response -> response.bodyToMono(AtlassianAccessToken.class))
                .block();

        assert atlassianAccessToken != null;

        atlassianAccessToken.setUserId(userId);

        log.info("Saving Access Token for user: {}", userId);

        atlassianAccessToken.setUserId(userId);
        atlassianAccessToken.setCreated(ZonedDateTime.now());
        atlassianAccessToken.setUpdated(ZonedDateTime.now());

        log.info("Saving a new access Access Token for user: {}", userId);
        log.info("Token has expiresIn: {}", atlassianAccessToken.getExpiresIn() != null);

        atlassianAccessTokenRepository.saveAndFlush(atlassianAccessToken);
    }

    public String accessibleResources() {
        String accessibleResources = apiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(OAUTH_PATH)
                        .pathSegment(TOKEN_PATH)
                        .pathSegment(ACCESSIBLE_RESOURCES_PATH)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(String.class))
                .block();

        log.info(accessibleResources);
        return accessibleResources;
    }
}
