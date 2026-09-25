package com.solesonic.mcp.security.identity;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The authenticated user a piece of work is being done for, carried as plain data.
 * <p>
 * Outbound calls to per-user APIs (Atlassian, Google) must not read {@link SecurityContextHolder}
 * at the point of use: graph nodes and fan-out work run on pooled threads (LangGraph4j runs graph
 * walks on {@code ForkJoinPool.commonPool()}) that never inherit the request's security context.
 * Instead, entry points ({@code @McpTool} methods, A2A executors) capture the caller once with
 * {@link #requireCurrent()}, pass it explicitly through graph state and service calls, and each
 * outbound request carries it as a {@link ClientRequest} attribute via {@link #requestAttributes()}.
 * <p>
 * Holds only the user id, never a credential — it is safe to persist in graph checkpoints; access
 * tokens are brokered fresh at the point of use.
 * <p>
 * {@link Serializable} because it lives in graph state: a checkpointed graph deep-copies its state
 * at every step with the graph's own serializer, which is Java serialization by default.
 */
public record CallerIdentity(UUID userId) implements Serializable {

    public static final String REQUEST_ATTRIBUTE = CallerIdentity.class.getName();

    public CallerIdentity {
        Objects.requireNonNull(userId, "userId cannot be null");
    }

    public static CallerIdentity of(String subject) {
        if (StringUtils.isBlank(subject)) {
            throw new IllegalArgumentException("Caller subject cannot be blank");
        }

        return new CallerIdentity(UUID.fromString(subject));
    }

    /**
     * Reads the caller from the current thread's security context. Only call this on the thread
     * that received the request.
     */
    public static CallerIdentity requireCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return of(jwt.getSubject());
        }

        throw new AuthenticationCredentialsNotFoundException("No authenticated JWT caller on the current request");
    }

    public Consumer<Map<String, Object>> requestAttributes() {
        return attributes -> attributes.put(REQUEST_ATTRIBUTE, this);
    }

    public static Optional<CallerIdentity> from(ClientRequest request) {
        return request.attribute(REQUEST_ATTRIBUTE)
                .filter(CallerIdentity.class::isInstance)
                .map(CallerIdentity.class::cast);
    }
}
