package com.solesonic.mcp.jira.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Provides TextEncryptor for encrypting tokens at rest using Spring Security Crypto.
 * Uses PBKDF2-derived key and AES with authenticated encryption.
 */
@Configuration
@ConditionalOnProperty(name = "mcp.jira.enabled", havingValue = "true")
public class CryptoConfig {

    @Bean
    public TextEncryptor tokenTextEncryptor(
            @Value("${encryption.password}") String password,
            @Value("${encryption.salt}") String saltHex
    ) {
        // Uses PBKDF2 to derive key and AES with authenticated encryption (GCM) for text.
        return Encryptors.delux(password, saltHex);
    }
}
