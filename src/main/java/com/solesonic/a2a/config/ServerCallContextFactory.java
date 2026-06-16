package com.solesonic.a2a.config;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.User;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ServerCallContextFactory {

    public ServerCallContext create() {
        Object principal = SecurityContextHolder.getContext().getAuthentication();
        User user = principal instanceof JwtAuthenticationToken jwtToken ? new JwtUser(jwtToken) : null;
        assert user != null;
        return new ServerCallContext(user, Map.of(), Set.of());
    }

    private record JwtUser(JwtAuthenticationToken token) implements User {
        @Override
        public boolean isAuthenticated() {
            return token.isAuthenticated();
        }

        @Override
        public @NonNull String getUsername() {
            return token.getName();
        }
    }
}
