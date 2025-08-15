package com.solesonic.mcp.jira.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solesonic.mcp.jira.auth.OAuthService;
import com.solesonic.mcp.jira.auth.PendingAuthStore;
import com.solesonic.mcp.jira.auth.UserProfileResolver;
import com.solesonic.mcp.jira.client.JiraClient;
import com.solesonic.mcp.jira.client.impl.JiraRestClientImpl;
import com.solesonic.mcp.jira.service.IdempotencyCache;
import com.solesonic.mcp.jira.service.RateLimiter;
import com.solesonic.mcp.jira.token.FileTokenStore;
import com.solesonic.mcp.jira.token.InMemoryTokenStore;
import com.solesonic.mcp.jira.token.TokenStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Jira-related beans behind feature flag mcp.jira.enabled.
 */
@Configuration
@EnableConfigurationProperties(AtlassianProperties.class)
@ConditionalOnProperty(name = "mcp.jira.enabled", havingValue = "true")
public class JiraFeatureConfig {

    @Bean
    public TokenStore tokenStore() {
        String base64Key = System.getenv("ENCRYPTION_KEY");
        String dir = System.getenv().getOrDefault("TOKEN_STORE_PATH", "tokens");
        if (base64Key != null && !base64Key.isBlank()) {
            try {
                return new FileTokenStore(Path.of(dir), base64Key);
            } catch (Exception ignored) {
            }
        }
        return new InMemoryTokenStore();
    }

    @Bean
    public PendingAuthStore pendingAuthStore() {
        return new PendingAuthStore(Duration.ofMinutes(10));
    }

    @Bean
    public UserProfileResolver userProfileResolver() {
        return new UserProfileResolver();
    }

    @Bean
    public OAuthService oAuthService(AtlassianProperties props, PendingAuthStore store, TokenStore tokenStore, RestClient.Builder builder) {
        return new OAuthService(props, store, tokenStore, builder);
    }

    @Bean
    public IdempotencyCache idempotencyCache() {
        return new IdempotencyCache(Duration.ofMinutes(3));
    }

    @Bean
    public RateLimiter rateLimiter() {
        return new RateLimiter(30, Duration.ofMinutes(1));
    }

    @Bean
    public JiraClient jiraClient(AtlassianProperties props, OAuthService oauth, RestClient.Builder builder, MeterRegistry registry) {
        return new JiraRestClientImpl(props, oauth, builder, registry);
    }
}
