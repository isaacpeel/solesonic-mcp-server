package com.solesonic.mcp.model.atlassian.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ProtectedResourceMetadata {
    private String issuer;
    private String resource;

    @JsonProperty("authorization_servers")
    private List<String> authorizationServers;

    @JsonProperty("scopes_supported")
    private List<String> scopesSupported;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    @SuppressWarnings("unused")
    public List<String> getScopesSupported() {
        return scopesSupported;
    }

    public void setScopesSupported(List<String> scopesSupported) {
        this.scopesSupported = scopesSupported;
    }

    @SuppressWarnings("unused")
    public List<String> getAuthorizationServers() {
        return authorizationServers;
    }

    public void setAuthorizationServers(List<String> authorizationServers) {
        this.authorizationServers = authorizationServers;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }
}
