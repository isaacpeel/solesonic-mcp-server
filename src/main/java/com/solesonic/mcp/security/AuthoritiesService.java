package com.solesonic.mcp.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class AuthoritiesService {
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
                    .map(role -> new SimpleGrantedAuthority(ROLE + role.toUpperCase()))
                    .map(GrantedAuthority.class::cast)
                    .toList();

        }

        return List.of();
    }
}
