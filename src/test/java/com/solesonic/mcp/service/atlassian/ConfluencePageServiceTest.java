package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.confluence.Page;
import com.solesonic.service.atlassian.ConfluencePageService;
import com.solesonic.testsupport.RecordingExchangeFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.util.UUID;

import static com.solesonic.testsupport.RecordingExchangeFunction.callerIdentityOf;
import static org.assertj.core.api.Assertions.assertThat;

class ConfluencePageServiceTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    private RecordingExchangeFunction backend;
    private ConfluencePageService service;

    @BeforeEach
    void setUp() {
        backend = new RecordingExchangeFunction();
        service = new ConfluencePageService(backend.webClient());
    }

    @Test
    void pages_carriesTheCaller() {
        service.pages(CALLER);

        assertThat(callerIdentityOf(backend.onlyRequest())).contains(CALLER);
    }

    @Test
    void get_carriesTheCaller() {
        backend.respondWithJson("{\"id\":\"42\",\"title\":\"Runbook\"}");

        Page page = service.get(CALLER, "42");

        assertThat(page.getId()).isEqualTo("42");
        assertThat(callerIdentityOf(backend.onlyRequest())).contains(CALLER);
    }

    @Test
    void createPage_carriesTheCaller() {
        Page page = new Page();
        page.setTitle("Runbook");

        service.createPage(CALLER, page);

        assertThat(backend.onlyRequest().method()).isEqualTo(HttpMethod.POST);
        assertThat(callerIdentityOf(backend.onlyRequest())).contains(CALLER);
    }

    @Test
    void updatePage_withoutVersion_carriesTheCallerOnTheLookupAndTheUpdate() {
        backend.respondWithJson("{\"id\":\"42\",\"version\":{\"number\":3}}");
        Page page = new Page();
        page.setId("42");

        service.updatePage(CALLER, page);

        assertThat(backend.requests()).extracting(ClientRequest::method)
                .containsExactly(HttpMethod.GET, HttpMethod.PUT);
        assertThat(backend.requests()).allSatisfy(request -> assertThat(callerIdentityOf(request)).contains(CALLER));
        assertThat(page.getVersion().getNumber()).isEqualTo(4);
    }

    @Test
    void deletePage_carriesTheCaller() {
        service.deletePage(CALLER, "42", false, false);

        assertThat(backend.onlyRequest().method()).isEqualTo(HttpMethod.DELETE);
        assertThat(callerIdentityOf(backend.onlyRequest())).contains(CALLER);
    }
}
