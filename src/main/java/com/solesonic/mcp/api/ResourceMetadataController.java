package com.solesonic.mcp.api;

import com.solesonic.mcp.model.atlassian.auth.ProtectedResourceMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ResourceMetadataController {

    public static final String WELL_KNOWN_OAUTH_PROTECTED_RESOURCE = "/.well-known/oauth-protected-resource";

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${solesonic.mcp.resource}")
    private String clientRegistrationResource;

    @GetMapping(WELL_KNOWN_OAUTH_PROTECTED_RESOURCE)
    public ProtectedResourceMetadata getResourceMetadata() {
        ProtectedResourceMetadata protectedResourceMetadata = new ProtectedResourceMetadata();
        protectedResourceMetadata.setResource(clientRegistrationResource);
        protectedResourceMetadata.setAuthorizationServers(List.of(issuerUri));
        protectedResourceMetadata.setScopesSupported(List.of("openid", "profile", "email"));

        return protectedResourceMetadata;
    }
}
