package com.solesonic.a2a.config;

import io.a2a.server.ServerCallContext;
import io.a2a.server.auth.User;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ServerCallContextFactory {

    public ServerCallContext create() {
        Object principal = SecurityContextHolder.getContext().getAuthentication();
        User user = principal instanceof JwtAuthenticationToken jwtToken ? new JwtUser(jwtToken) : null;
        return new ServerCallContext(user, Map.of(), Set.of());
    }

    private record JwtUser(JwtAuthenticationToken token) implements User {
        @Override
        public boolean isAuthenticated() {
            return token.isAuthenticated();
        }

        @Override
        public String getUsername() {
            return token.getName();
        }
    }
}
