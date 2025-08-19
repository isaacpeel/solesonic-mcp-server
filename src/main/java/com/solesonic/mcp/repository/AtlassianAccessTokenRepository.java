package com.solesonic.mcp.repository;

import com.solesonic.mcp.model.atlassian.auth.AtlassianAccessToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AtlassianAccessTokenRepository {

    public Optional<AtlassianAccessToken> findAdminUser() {
        return Optional.empty();
    }

    public Optional<AtlassianAccessToken> findById(UUID userId) {
        return Optional.empty();
    }

    public AtlassianAccessToken saveAndFlush(AtlassianAccessToken atlassianAccessToken) {
        return atlassianAccessToken;
    }
}
