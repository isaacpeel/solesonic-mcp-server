package com.solesonic.model.google.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;

import java.util.UUID;

/**
 * Request body for {@code POST /broker/google/token}. Unlike the Atlassian broker there is no
 * {@code audience} discriminator — the endpoint itself identifies the provider.
 */
public record GoogleTokenExchange(
        @Nonnull
        @JsonProperty("subject_token")
        UUID subjectToken) {
}
