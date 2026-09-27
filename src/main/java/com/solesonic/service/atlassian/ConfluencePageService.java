package com.solesonic.service.atlassian;

import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.confluence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.service.atlassian.ConfluenceConstants.*;

@Service
public class ConfluencePageService {
    private static final Logger log = LoggerFactory.getLogger(ConfluencePageService.class);

    private final WebClient webClient;

    public ConfluencePageService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    public ConfluencePagesResponse pages(CallerIdentity callerIdentity) {
        log.info("Getting Confluence documents.");
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .pathSegment(PAGES_PATH)
                        .queryParam("body-format", STORAGE_FORMAT)
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .exchangeToMono(response -> response.bodyToMono(ConfluencePagesResponse.class))
                .block();
    }

    public Page get(CallerIdentity callerIdentity, String id) {
        log.info("Getting Confluence document: {}", id);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .pathSegment(PAGES_PATH)
                        .pathSegment(id)
                        .queryParam("body-format", STORAGE_FORMAT)
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .exchangeToMono(response -> response.bodyToMono(Page.class))
                .block();
    }

    /**
     * Creates a new page in Confluence.
     *
     * @param callerIdentity The user the page is created for
     * @param page The Page object containing all the necessary information
     * @return The created page
     */
    public Page createPage(CallerIdentity callerIdentity, Page page) {
        log.info("Creating Confluence page: {} in space: {}", page.getTitle(), page.getSpaceId());

        // Ensure the body and storage are properly set up
        if (page.getBody() == null) {
            Body body = new Body();
            Storage storage = new Storage();
            storage.setRepresentation(STORAGE_FORMAT);
            storage.setValue("");
            body.setStorage(storage);
            page.setBody(body);
        } else if (page.getBody().getStorage() == null) {
            Storage storage = new Storage();
            storage.setRepresentation(STORAGE_FORMAT);
            storage.setValue("");
            page.getBody().setStorage(storage);
        } else if (page.getBody().getStorage().getRepresentation() == null) {
            page.getBody().getStorage().setRepresentation(STORAGE_FORMAT);
        }

        // Set status to current (published) if not specified
        if (page.getStatus() == null) {
            page.setStatus("current");
        }

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .pathSegment(PAGES_PATH)
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .bodyValue(page)
                .exchangeToMono(response -> response.bodyToMono(Page.class))
                .block();
    }

    /**
     * Updates an existing page in Confluence.
     *
     * @param callerIdentity The user the page is updated for
     * @param page           The Page object containing all the necessary information for the update
     */
    public void updatePage(CallerIdentity callerIdentity, Page page) {
        String id = page.getId();
        log.info("Updating Confluence page: {}", id);

        // Ensure the body and storage are properly set up
        if (page.getBody() == null) {
            Body body = new Body();
            Storage storage = new Storage();
            storage.setRepresentation(STORAGE_FORMAT);
            storage.setValue("");
            body.setStorage(storage);
            page.setBody(body);
        } else if (page.getBody().getStorage() == null) {
            Storage storage = new Storage();
            storage.setRepresentation(STORAGE_FORMAT);
            storage.setValue("");
            page.getBody().setStorage(storage);
        } else if (page.getBody().getStorage().getRepresentation() == null) {
            page.getBody().getStorage().setRepresentation(STORAGE_FORMAT);
        }

        // Ensure version is set
        if (page.getVersion() == null) {
            // Get the current page to get the version
            Page currentPage = get(callerIdentity, id);
            Version version = new Version();
            version.setNumber(currentPage.getVersion().getNumber() + 1);
            page.setVersion(version);
        }

        webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .pathSegment(PAGES_PATH)
                        .pathSegment(id)
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .bodyValue(page)
                .exchangeToMono(response -> response.bodyToMono(Page.class))
                .block();
    }

    /**
     * Deletes a page in Confluence.
     *
     * @param callerIdentity The user the page is deleted for
     * @param id The ID of the page to delete
     * @param purge If true, permanently deletes the page (requires space admin permissions)
     * @param draft If true, deletes a draft page
     */
    public void deletePage(CallerIdentity callerIdentity, String id, boolean purge, boolean draft) {
        log.info("Deleting Confluence page: {}, purge: {}, draft: {}", id, purge, draft);

        webClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .pathSegment(PAGES_PATH)
                        .pathSegment(id)
                        .queryParamIfPresent("purge", purge ? Optional.of(true) : Optional.empty())
                        .queryParamIfPresent("draft", draft ? Optional.of(true) : Optional.empty())
                        .build())
                .attributes(callerIdentity.requestAttributes())
                .exchangeToMono(response -> response.bodyToMono(Void.class))
                .block();
    }
}
