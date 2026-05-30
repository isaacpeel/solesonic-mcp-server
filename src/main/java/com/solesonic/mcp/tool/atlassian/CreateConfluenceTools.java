package com.solesonic.mcp.tool.atlassian;

import com.solesonic.mcp.model.atlassian.confluence.Body;
import com.solesonic.mcp.model.atlassian.confluence.Page;
import com.solesonic.mcp.model.atlassian.confluence.Storage;
import com.solesonic.mcp.service.atlassian.ConfluencePageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import static com.solesonic.mcp.service.atlassian.ConfluenceConstants.STORAGE_FORMAT;

@Component
@SuppressWarnings("unused")
public class CreateConfluenceTools {
    private static final Logger log = LoggerFactory.getLogger(CreateConfluenceTools.class);

    public static final String CREATE_CONFLUENCE_PAGE = "create_confluence_page";
    public static final String DEFAULT_SPACE_ID = "16154645";
    public static final String CONFLUENCE_URL_TEMPLATE = "https://solesonic-llm-api.atlassian.net/wiki/spaces/{spaceId}/pages/{pageId}";

    private static final String CREATE_CONFLUENCE_PAGE_DESCRIPTION = """
            Creates a new Confluence page in the default workspace space and returns its identifiers.
            """;

    private final ConfluencePageService confluencePageService;

    public CreateConfluenceTools(ConfluencePageService confluencePageService) {
        this.confluencePageService = confluencePageService;
    }

    public record CreateConfluencePageResponse(String pageId, String pageUri) {}
    public record CreateConfluencePageRequest(
            @McpToolParam(description = "Required page title. Make it clear, specific, and searchable.") String title,
            @McpToolParam(description = "Required page body. Prefer Confluence 'storage' format; plain text accepted.") String content) {}

    @SuppressWarnings("unused")
    @McpTool(name = CREATE_CONFLUENCE_PAGE, description = CREATE_CONFLUENCE_PAGE_DESCRIPTION)
    public CreateConfluencePageResponse createConfluencePage(CreateConfluencePageRequest request) {
        log.debug("Invoking create confluence page function");
        log.debug("Title: {}", request.title);
        log.debug("Content length: {}", request.content != null ? request.content.length() : 0);

        Page page = new Page();
        page.setTitle(request.title);
        page.setSpaceId(DEFAULT_SPACE_ID);
        page.setStatus("current");

        Body body = new Body();
        Storage storage = new Storage();
        storage.setRepresentation(STORAGE_FORMAT);
        storage.setValue(request.content != null ? request.content : "");
        body.setStorage(storage);
        page.setBody(body);

        Page createdPage = confluencePageService.createPage(page);
        log.debug("Created confluence page: {}", createdPage.getId());
        String pageUri = CONFLUENCE_URL_TEMPLATE
                .replace("{spaceId}", DEFAULT_SPACE_ID)
                .replace("{pageId}", createdPage.getId());
        log.debug("Using page uri: {}", pageUri);
        return new CreateConfluencePageResponse(createdPage.getId(), pageUri);
    }
}
