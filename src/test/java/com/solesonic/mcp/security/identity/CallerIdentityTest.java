package com.solesonic.mcp.security.identity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallerIdentityTest {

    private static final UUID USER_ID = UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireCurrent_readsTheJwtSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .issuedAt(Instant.now())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        CallerIdentity callerIdentity = CallerIdentity.requireCurrent();

        assertEquals(USER_ID, callerIdentity.userId());
    }

    @Test
    void requireCurrent_withoutAuthentication_throws() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, CallerIdentity::requireCurrent);
    }

    @Test
    void requireCurrent_withNonJwtPrincipal_throws() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("someone", "password"));

        assertThrows(AuthenticationCredentialsNotFoundException.class, CallerIdentity::requireCurrent);
    }

    @Test
    void of_rejectsBlankSubject() {
        assertThrows(IllegalArgumentException.class, () -> CallerIdentity.of(" "));
    }

    @Test
    void requestAttributes_roundTripThroughClientRequest() {
        CallerIdentity callerIdentity = new CallerIdentity(USER_ID);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://example.test/resource"))
                .attributes(callerIdentity.requestAttributes())
                .build();

        assertEquals(Optional.of(callerIdentity), CallerIdentity.from(request));
    }

    @Test
    void from_requestWithoutAttribute_isEmpty() {
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://example.test/resource")).build();

        assertTrue(CallerIdentity.from(request).isEmpty());
    }
}
