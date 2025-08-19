package com.solesonic.mcp.security;

import com.solesonic.mcp.scope.UserRequestContext;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
@Profile({"prod"})
public class JwtUserRequestFilter extends OncePerRequestFilter {

    public static final String SUB = "sub";
    private final UserRequestContext userRequestContext;

    public JwtUserRequestFilter(UserRequestContext userRequestContext) {
        this.userRequestContext = userRequestContext;
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString(SUB);

            if (userId != null) {
                userRequestContext.setUserId(UUID.fromString(userId));
            }
        } else {
            //If there is no authentication, always throw an exception
            throw new IllegalStateException("Anonymous authentication is not allowed.");
        }

        filterChain.doFilter(request, response);
    }
}
