package com.solesonic.mcp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class AuthoritiesService {
    private static final Logger log =  LoggerFactory.getLogger(AuthoritiesService.class);

    public static final String GROUP = "GROUP_";
    public static final String GROUPS = "groups";

    public static final String ROLE = "ROLE_";
    public static final String ROLES = "roles";

    public Collection<GrantedAuthority> extractGroupAuthorities(Jwt jwt) {
        Object groupsClaim = jwt.getClaim(GROUPS);


        if (groupsClaim instanceof List<?> groups) {
            return groups.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .peek(group -> log.trace("User belongs to group: {}", group))
                    .map(group -> new SimpleGrantedAuthority(GROUP + group.toUpperCase()))
                    .map(GrantedAuthority.class::cast)
                    .toList();

        }

        return List.of();
    }

    public Collection<GrantedAuthority> extractRoleAuthorities(Jwt jwt) {
        Object rolesClaim = jwt.getClaim(ROLES);

        if (rolesClaim instanceof List<?> roles) {
            return  roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .peek(role -> log.trace("User belongs to role: {}", role))
                    .map(role -> new SimpleGrantedAuthority(ROLE + role.toUpperCase()))
                    .map(GrantedAuthority.class::cast)
                    .toList();

        }

        return List.of();
    }
}
