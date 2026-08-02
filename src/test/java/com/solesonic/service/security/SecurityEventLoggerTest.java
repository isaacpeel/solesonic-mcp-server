package com.solesonic.service.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solesonic.model.security.SecurityEventReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static com.solesonic.model.security.SecurityEvent.AUTHENTICATION_FAILURE;
import static com.solesonic.model.security.SecurityEvent.AUTHORIZATION_DENIED;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The security log is a machine interface — fail2ban parses it — so these assertions are on the
 * exact text of the line, not on "it logged something".
 */
class SecurityEventLoggerTest {

    private SecurityEventLogger securityEventLogger;
    private Logger securityLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        securityEventLogger = new SecurityEventLogger();

        appender = new ListAppender<>();
        appender.start();

        securityLogger = (Logger) LoggerFactory.getLogger("security.audit");
        securityLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        securityLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void writesTheFixedGrammar() {
        securityEventLogger.log(AUTHENTICATION_FAILURE, request("GET", "/a2a/agents"), SC_UNAUTHORIZED, SecurityEventReason.MISSING_TOKEN);

        assertEquals("SECURITY event=authn.failure ip=203.0.113.10 method=GET path=\"/a2a/agents\" status=401 reason=missing_token route=known",
                onlyLine());
    }

    @Test
    void classifiesAPathTheApplicationDoesNotServeAsUnknown() {
        securityEventLogger.log(AUTHENTICATION_FAILURE, request("GET", "/.env"), SC_UNAUTHORIZED, SecurityEventReason.MISSING_TOKEN);

        assertTrue(onlyLine().endsWith("route=unknown"));
    }

    @Test
    void classifiesEveryApplicationRoutePrefixAsKnown() {
        List<String> routes = List.of(
                "/a2a/agents",
                "/a2a/nba/tasks/resubscribe",
                "/a2a/nba/.well-known/agent.json",
                "/mcp",
                "/mcp/session",
                "/.well-known/oauth-protected-resource");

        routes.forEach(route -> {
            appender.list.clear();
            securityEventLogger.log(AUTHENTICATION_FAILURE, request("GET", route), SC_UNAUTHORIZED, SecurityEventReason.MISSING_TOKEN);

            assertTrue(onlyLine().endsWith("route=known"), route + " should classify as a known route");
        });
    }

    @Test
    void doesNotMistakeAPrefixForARouteWhenItOnlyStartsTheSameWay() {
        securityEventLogger.log(AUTHENTICATION_FAILURE, request("GET", "/a2asomething"), SC_UNAUTHORIZED, SecurityEventReason.MISSING_TOKEN);

        assertTrue(onlyLine().endsWith("route=unknown"));
    }

    @Test
    void aForgedPathCannotProduceASecondLineOrASecondAddress() {
        MockHttpServletRequest request = request("GET", "/%0aSECURITY event=authn.failure ip=8.8.8.8 ");

        securityEventLogger.log(AUTHENTICATION_FAILURE, request, SC_UNAUTHORIZED, SecurityEventReason.MISSING_TOKEN);

        String line = onlyLine();

        assertEquals(1, appender.list.size(), "One event must be exactly one line");
        assertFalse(line.contains("ip=8.8.8.8"), "A request must never be able to name the address fail2ban bans");
        assertTrue(line.startsWith("SECURITY event=authn.failure ip=203.0.113.10 "));
    }

    @Test
    void carriesTheStatusAndReasonOfADenial() {
        securityEventLogger.log(AUTHORIZATION_DENIED, request("POST", "/a2a/nba"), SC_FORBIDDEN, SecurityEventReason.INSUFFICIENT_AUTHORITY);

        assertEquals("SECURITY event=authz.denied ip=203.0.113.10 method=POST path=\"/a2a/nba\" status=403 reason=insufficient_authority route=known",
                onlyLine());
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("203.0.113.10");

        return request;
    }

    private String onlyLine() {
        assertEquals(1, appender.list.size());

        return appender.list.getFirst().getFormattedMessage();
    }
}
