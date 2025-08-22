package com.solesonic.mcp.security;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Order(1)
public class JwtUserRequestFilter extends OncePerRequestFilter {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(JwtUserRequestFilter.class);

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {
        Principal userPrincipal = request.getUserPrincipal();

        String requestURI = request.getRequestURI();

        if(requestURI.contains("message")) {
//            String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
//            log.info("Request body: {}", body);

        }

        if(userPrincipal instanceof JwtAuthenticationToken jwtAuth) {
            Map<String, Object> tokenAttributes = jwtAuth.getTokenAttributes();
            Object name = tokenAttributes.get("username");

            if(name != null) {
                log.info("Request to `{}` secured for user:  {}", requestURI, name);
            } else {
                String clientId = tokenAttributes.get("client_id").toString();
                log.info("Request to `{}` secured for client id:  {}", requestURI, clientId);
            }
        }

        filterChain.doFilter(request, response);
    }
}
