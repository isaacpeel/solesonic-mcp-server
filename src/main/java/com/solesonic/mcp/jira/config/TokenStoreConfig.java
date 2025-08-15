package com.solesonic.mcp.jira.config;

import com.solesonic.mcp.jira.token.JpaTokenStore;
import com.solesonic.mcp.jira.token.TokenRepository;
import com.solesonic.mcp.jira.token.TokenStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "mcp.jira.enabled", havingValue = "true")
public class TokenStoreConfig {

    @Bean
    public TokenStore tokenStore(TokenRepository repository) {
        return new JpaTokenStore(repository);
    }
}
