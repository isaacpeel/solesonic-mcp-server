package com.solesonic.mcp.jira.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.solesonic.mcp.jira.config.AtlassianProperties;
import com.solesonic.mcp.jira.error.ToolErrorCode;
import com.solesonic.mcp.jira.error.ToolException;
import com.solesonic.mcp.jira.token.StoredToken;
import com.solesonic.mcp.jira.token.TokenStore;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Minimal OAuth2 (Atlassian) service implementing PKCE auth URL generation, token exchange, and refresh.
 * Avoids Spring Security's authorized client manager for simplicity and control.
 */
public class OAuthService {
    private static final String AUTH_URL = "https://auth.atlassian.com/authorize";
    private static final String TOKEN_URL = "https://auth.atlassian.com/oauth/token";

    private final AtlassianProperties props;
    private final PendingAuthStore pendingAuthStore;
    private final TokenStore tokenStore;
    private final RestClient http;

    public OAuthService(AtlassianProperties props,
                        PendingAuthStore pendingAuthStore,
                        TokenStore tokenStore,
                        RestClient.Builder restBuilder) {
        this.props = props;
        this.pendingAuthStore = pendingAuthStore;
        this.tokenStore = tokenStore;
        this.http = restBuilder.baseUrl("") // absolute URLs used
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public record AuthUrl(String url, String state, Instant expiresAt) {}

    public AuthUrl getAuthUrl(String userProfileId) {
        String clientId = required(props.getClientId(), "atlassian.client-id");
        String redirectUri = required(props.getRedirectUri(), "atlassian.redirect-uri");
        String scopes = required(props.getScopes(), "atlassian.scopes");

        String codeVerifier = generateCodeVerifier();
        String codeChallenge = codeChallengeS256(codeVerifier);
        PendingAuthStore.Pending pending = pendingAuthStore.create(userProfileId, codeVerifier);

        String url = AUTH_URL + "?audience=api.atlassian.com" +
                "&client_id=" + urlEncode(clientId) +
                "&scope=" + urlEncode(scopes) +
                "&redirect_uri=" + urlEncode(redirectUri) +
                "&state=" + urlEncode(pending.state()) +
                "&response_type=code" +
                "&prompt=consent" +
                "&code_challenge=" + urlEncode(codeChallenge) +
                "&code_challenge_method=S256";

        return new AuthUrl(url, pending.state(), pending.expiresAt());
    }

    public List<String> completeAuth(String userProfileId, String state, String code) {
        var verifierOpt = pendingAuthStore.consumeAndGetVerifier(userProfileId, state);
        if (verifierOpt.isEmpty()) {
            throw new ToolException(ToolErrorCode.AUTH_REQUIRED, "Invalid or expired OAuth state");
        }

        String clientId = required(props.getClientId(), "atlassian.client-id");
        String redirectUri = required(props.getRedirectUri(), "atlassian.redirect-uri");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("client_id", clientId);
        body.put("code", code);
        body.put("code_verifier", verifierOpt.get());
        body.put("redirect_uri", redirectUri);

        TokenResponse tr = http.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);
        if (tr == null || tr.accessToken == null) {
            throw new ToolException(ToolErrorCode.AUTH_REQUIRED, "Failed to exchange authorization code");
        }
        List<String> scopes = parseScopes(tr.scope);
        StoredToken token = new StoredToken(tr.tokenType, tr.accessToken, tr.refreshToken, Instant.now().plusSeconds(tr.expiresIn), scopes);
        tokenStore.save(userProfileId, required(props.getCloudId(), "atlassian.cloud-id"), token);
        return scopes;
    }

    public Optional<String> getValidAccessToken(String userProfileId) {
        var opt = tokenStore.get(userProfileId, required(props.getCloudId(), "atlassian.cloud-id"));
        if (opt.isEmpty()) return Optional.empty();
        StoredToken t = opt.get();
        if (t.expiresAt() == null || t.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return Optional.ofNullable(t.accessToken());
        }
        try {
            var refreshed = refresh(userProfileId);
            return refreshed.map(StoredToken::accessToken);
        } catch (ToolException ex) {
            return Optional.empty();
        }
    }

    public Optional<StoredToken> refresh(String userProfileId) {
        var opt = tokenStore.get(userProfileId, required(props.getCloudId(), "atlassian.cloud-id"));
        if (opt.isEmpty()) throw new ToolException(ToolErrorCode.AUTH_REQUIRED, "No stored credentials");
        StoredToken st = opt.get();
        if (st.refreshToken() == null || st.refreshToken().isBlank()) {
            throw new ToolException(ToolErrorCode.AUTH_EXPIRED, "Missing refresh token; re-auth required");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", "refresh_token");
        body.put("client_id", required(props.getClientId(), "atlassian.client-id"));
        body.put("refresh_token", st.refreshToken());

        TokenResponse tr = http.post().uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(TokenResponse.class);
        if (tr == null || tr.accessToken == null) {
            tokenStore.delete(userProfileId, required(props.getCloudId(), "atlassian.cloud-id"));
            throw new ToolException(ToolErrorCode.AUTH_EXPIRED, "Failed to refresh token");
        }
        List<String> scopes = parseScopes(tr.scope);
        StoredToken nt = new StoredToken(tr.tokenType, tr.accessToken, tr.refreshToken != null ? tr.refreshToken : st.refreshToken(), Instant.now().plusSeconds(tr.expiresIn), scopes);
        tokenStore.save(userProfileId, required(props.getCloudId(), "atlassian.cloud-id"), nt);
        return Optional.of(nt);
    }

    private static List<String> parseScopes(String scope) {
        if (scope == null) return Collections.emptyList();
        String[] parts = scope.split(" ");
        return Arrays.asList(parts);
    }

    private static String required(String v, String name) {
        Assert.hasText(v, "Missing property: " + name);
        return v;
    }

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String codeChallengeS256(String verifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // Token response mapping
    public static class TokenResponse {
        @JsonProperty("token_type")
        public String tokenType;
        @JsonProperty("access_token")
        public String accessToken;
        @JsonProperty("refresh_token")
        public String refreshToken;
        @JsonProperty("expires_in")
        public long expiresIn;
        public String scope;
    }
}
