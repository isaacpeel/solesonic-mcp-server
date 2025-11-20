package com.solesonic.mcp.tool.atlassian;

import com.solesonic.mcp.model.atlassian.confluence.Body;
import com.solesonic.mcp.model.atlassian.confluence.Page;
import com.solesonic.mcp.model.atlassian.confluence.Storage;
import com.solesonic.mcp.service.atlassian.ConfluencePageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import static com.solesonic.mcp.service.atlassian.ConfluenceConstants.STORAGE_FORMAT;

@Component
public class CreateConfluenceTools {
    private static final Logger log = LoggerFactory.getLogger(CreateConfluenceTools.class);
    
    public static final String CREATE_CONFLUENCE_PAGE = "create_confluence_page";
    public static final String DEFAULT_SPACE_ID = "16154645";
    public static final String CONFLUENCE_URL_TEMPLATE = "https://solesonic-llm-api.atlassian.net/wiki/spaces/{spaceId}/pages/{pageId}";

    private static final String CREATE_CONFLUENCE_PAGE_DESCRIPTION = """
            Creates a new Confluence page in the default workspace space and returns its identifiers.
            Use this when you need to capture requirements, design docs, meeting notes, or summaries that should
            persist outside the current chat. The page will be created in the default space and set to status "current".

            Inputs:
            - title (required): A concise, human‑readable page title. Prefer specific, searchable titles
              (for example: "API: Password Reset Flow – Design v1"). Avoid emojis and ambiguous phrasing.
            - content (required): The page body content. Rich content is supported; provide Confluence "storage" format
              if available. Plain text is accepted and will be stored as‑is. Consider including:
              - Background/Context and Objectives
              - Key Decisions and Action Items
              - Acceptance Criteria or Checklists
              - Links to related Jira issues or boards. You can obtain these links using Jira tools:
                • create_jira_issue (to create and link new issues)
                • list_jira_boards / get_jira_board / get_jira_board_issues (to reference boards and issues)

            Returns:
            - pageId: The Confluence page ID.
            - pageUri: A direct URL to the created page.

            Notes and best practices:
            - Idempotency is NOT enforced server‑side. Avoid repeated calls for the same logical document unless updating
              content intentionally via a follow‑up mechanism.
            - The page is created in the default Confluence space configured by the server.
            - Content is stored using the Confluence "storage" representation.
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

        // Create the page object
        Page page = new Page();
        page.setTitle(request.title);
        page.setSpaceId(DEFAULT_SPACE_ID);
        page.setStatus("current");

        // Set up the body with content
        Body body = new Body();
        Storage storage = new Storage();
        storage.setRepresentation(STORAGE_FORMAT);
        storage.setValue(request.content != null ? request.content : "");
        body.setStorage(storage);
        page.setBody(body);

        // Create the page in Confluence
        Page createdPage = confluencePageService.createPage(page);
        
        log.debug("Created confluence page: {}", createdPage.getId());

        // Generate the page URI
        String pageUri = CONFLUENCE_URL_TEMPLATE
                .replace("{spaceId}", DEFAULT_SPACE_ID)
                .replace("{pageId}", createdPage.getId());

        log.debug("Using page uri: {}", pageUri);

        return new CreateConfluencePageResponse(createdPage.getId(), pageUri);
    }
}