package com.solesonic.mcp.jira.token;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "jira_tokens")
public class TokenEntity {

    @EmbeddedId
    private TokenId id;

    @Convert(converter = TokenEncryptionConverter.class)
    @Column(name = "encrypted_payload", nullable = false, columnDefinition = "text")
    private String encryptedPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TokenEntity() {}

    public TokenEntity(TokenId id, String encryptedPayload) {
        this.id = id;
        this.encryptedPayload = encryptedPayload;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public TokenId getId() { return id; }
    public String getEncryptedPayload() { return encryptedPayload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setEncryptedPayload(String encryptedPayload) { this.encryptedPayload = encryptedPayload; }

    @Embeddable
    public static class TokenId implements Serializable {
        @Column(name = "user_profile_id", nullable = false, length = 200)
        private String userProfileId;

        @Column(name = "cloud_id", nullable = false, length = 200)
        private String cloudId;

        public TokenId() {}

        public TokenId(String userProfileId, String cloudId) {
            this.userProfileId = userProfileId;
            this.cloudId = cloudId;
        }

        public String getUserProfileId() { return userProfileId; }
        public String getCloudId() { return cloudId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (!(o instanceof TokenId)) { return false; }
            TokenId tokenId = (TokenId) o;
            return Objects.equals(userProfileId, tokenId.userProfileId)
                && Objects.equals(cloudId, tokenId.cloudId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userProfileId, cloudId);
        }
    }
}
