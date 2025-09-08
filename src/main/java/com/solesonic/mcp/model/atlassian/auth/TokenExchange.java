package com.solesonic.mcp.model.atlassian.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.util.UUID;

public record TokenExchange(
        @NotNull
        @JsonProperty("subject_token")
        UUID subjectToken,
        String audience) {
}