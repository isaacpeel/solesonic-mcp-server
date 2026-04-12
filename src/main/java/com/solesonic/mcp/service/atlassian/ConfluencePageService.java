package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.model.atlassian.confluence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;
import static com.solesonic.mcp.service.atlassian.ConfluenceConstants.*;

@Service
public class ConfluencePageService {
    private static final Logger log = LoggerFactory.getLogger(ConfluencePageService.class);

    private final WebClient webClient;

    public ConfluencePageService(@Qualifier(ATLASSIAN_API_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<ConfluencePagesResponse> pages() {
        log.info("Getting Confluence documents.");
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .pathSegment(PAGES_PATH)
                        .queryParam("body-format", STORAGE_FORMAT)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(ConfluencePagesResponse.class))
                .doOnSuccess(_ -> log.info("Confluence pages retrieved"));
    }

    public Mono<Page> get(String id) {
        log.info("Getting Confluence document: {}", id);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .pathSegment(PAGES_PATH)
                        .pathSegment(id)
                        .queryParam("body-format", STORAGE_FORMAT)
                        .build())
                .exchangeToMono(response -> response.bodyToMono(Page.class))
                .doOnSuccess(_ -> log.info("Confluence document retrieved: {}", id));
    }

    /**
     * Creates a new page in Confluence.
     *
     * @param page The Page object containing all the necessary information
     * @return The created page
     */
    public Mono<Page> createPage(Page page) {
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
                .bodyValue(page)
                .exchangeToMono(response -> response.bodyToMono(Page.class))
                .doOnSuccess(createdPage -> log.info("Confluence page created: {}", createdPage != null ? createdPage.getId() : null));
    }

    /**
     * Updates an existing page in Confluence.
     *
     * @param page The Page object containing all the necessary information for the update
     * @return The updated page
     */
    @SuppressWarnings("unused")
    public Mono<Page> updatePage(Page page) {
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

        Mono<Page> preparedPageMono = page.getVersion() == null
                ? get(id).map(currentPage -> {
                    Version version = new Version();
                    version.setNumber(currentPage.getVersion().getNumber() + 1);
                    page.setVersion(version);
                    return page;
                })
                : Mono.just(page);

        return preparedPageMono.flatMap(preparedPage -> webClient.put()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment(basePathSegments)
                                .pathSegment(PAGES_PATH)
                                .pathSegment(id)
                                .build())
                        .bodyValue(preparedPage)
                        .exchangeToMono(response -> response.bodyToMono(Page.class)))
                .doOnSuccess(updatedPage -> log.info("Confluence page updated: {}", id));
    }

    /**
     * Deletes a page in Confluence.
     *
     * @param id The ID of the page to delete
     * @param purge If true, permanently deletes the page (requires space admin permissions)
     * @param draft If true, deletes a draft page
     */
    @SuppressWarnings("unused")
    public Mono<Void> deletePage(String id, boolean purge, boolean draft) {
        log.info("Deleting Confluence page: {}, purge: {}, draft: {}", id, purge, draft);

        return webClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(basePathSegments)
                        .pathSegment(PAGES_PATH)
                        .pathSegment(id)
                        .queryParamIfPresent("purge", purge ? java.util.Optional.of(true) : java.util.Optional.empty())
                        .queryParamIfPresent("draft", draft ? java.util.Optional.of(true) : java.util.Optional.empty())
                        .build())
                .exchangeToMono(response -> response.bodyToMono(Void.class))
                .then();
    }
}
