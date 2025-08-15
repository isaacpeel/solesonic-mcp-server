package com.solesonic.mcp.jira.token;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class JpaTokenStore implements TokenStore {

    private final TokenRepository repository;

    public JpaTokenStore(TokenRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredToken> get(String userProfileId, String cloudId) {
        TokenEntity.TokenId id = new TokenEntity.TokenId(userProfileId, cloudId);
        return repository.findById(id)
                .map(TokenEntity::getEncryptedPayload)
                .map(this::fromPayload);
    }

    @Override
    @Transactional
    public void save(String userProfileId, String cloudId, StoredToken token) {
        TokenEntity.TokenId id = new TokenEntity.TokenId(userProfileId, cloudId);
        String payload = toPayload(token);
        TokenEntity entity = repository.findById(id)
                .orElse(new TokenEntity(id, payload));
        entity.setEncryptedPayload(payload);
        repository.save(entity);
    }

    @Override
    @Transactional
    public void delete(String userProfileId, String cloudId) {
        TokenEntity.TokenId id = new TokenEntity.TokenId(userProfileId, cloudId);
        repository.deleteById(id);
    }

    private String toPayload(StoredToken token) {
        String scopesJoined = token.scopes() == null ? "" : String.join(" ", token.scopes());
        long expiresEpochSeconds = token.expiresAt() == null ? 0L : token.expiresAt().getEpochSecond();
        return String.join("\n",
                nullToEmpty(token.tokenType()),
                nullToEmpty(token.accessToken()),
                nullToEmpty(token.refreshToken()),
                Long.toString(expiresEpochSeconds),
                scopesJoined
        );
    }

    private StoredToken fromPayload(String payloadString) {
        String[] payloadLines = payloadString.split("\n", -1);
        String tokenType = emptyToNull(payloadLines.length > 0 ? payloadLines[0] : null);
        String accessToken = emptyToNull(payloadLines.length > 1 ? payloadLines[1] : null);
        String refreshToken = emptyToNull(payloadLines.length > 2 ? payloadLines[2] : null);
        long expiresEpochSeconds = 0L;
        try { expiresEpochSeconds = Long.parseLong(payloadLines.length > 3 ? payloadLines[3] : "0"); } catch (Exception ignored) {}
        Instant expiresAt = expiresEpochSeconds > 0 ? Instant.ofEpochSecond(expiresEpochSeconds) : null;
        List<String> scopes = List.of();
        if (payloadLines.length > 4 && payloadLines[4] != null && !payloadLines[4].isBlank()) {
            scopes = List.of(payloadLines[4].split(" "));
        }
        return new StoredToken(tokenType, accessToken, refreshToken, expiresAt, scopes);
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static String emptyToNull(String value) { return (value == null || value.isEmpty()) ? null : value; }
}
