package com.solesonic.mcp.jira.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("atlassian")
public class AtlassianProperties {
    /** Atlassian OAuth2 client ID */
    private String clientId;
    /** Atlassian OAuth2 client secret (may be optional with PKCE) */
    private String clientSecret;
    /** Redirect URI for OAuth2 callback (e.g., <a href="http://127.0.0.1:8765/oauth/callback/atlassian">...</a>) */
    private String redirectUri;
    /** Atlassian Cloud ID */
    private String cloudId;
    /** Jira project ID */
    private String projectId;
    /** Jira issue type ID */
    private String issueTypeId;
    /** Space-separated scopes */
    private String scopes;
    /** Jira base URL (e.g., <a href="https://your-domain.atlassian.net">...</a>) */
    private String jiraBaseUrl;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getCloudId() {
        return cloudId;
    }

    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getIssueTypeId() {
        return issueTypeId;
    }

    public void setIssueTypeId(String issueTypeId) {
        this.issueTypeId = issueTypeId;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getJiraBaseUrl() {
        return jiraBaseUrl;
    }

    public void setJiraBaseUrl(String jiraBaseUrl) {
        this.jiraBaseUrl = jiraBaseUrl;
    }
}
