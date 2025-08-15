package com.solesonic.mcp.jira.web;

import com.solesonic.mcp.jira.auth.OAuthService;
import com.solesonic.mcp.jira.auth.UserProfileResolver;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "mcp.jira.enabled", havingValue = "true")
public class OAuthCallbackController {

    private final OAuthService oauth;
    private final UserProfileResolver resolver;

    public OAuthCallbackController(OAuthService oauth, UserProfileResolver resolver) {
        this.oauth = oauth;
        this.resolver = resolver;
    }

    @GetMapping(value = "/oauth/callback/atlassian", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam("code") String code, @RequestParam("state") String state) {
        String user = resolver.currentProfileId();
        try {
            oauth.completeAuth(user, state, code);
            return "<html><body><h3>Jira authorization successful.</h3><p>You may close this window.</p></body></html>";
        } catch (Exception e) {
            return "<html><body><h3>Jira authorization failed.</h3><p>" + e.getMessage() + "</p></body></html>";
        }
    }
}
