package com.solesonic.mcp.exception.atlassian;

import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pulls the human-readable messages out of Atlassian error bodies.
 * <p>
 * Jira returns {@code {"errorMessages": [...], "errors": {"field": "message"}}}; Confluence v2
 * returns {@code {"errors": [{"title": "..."}]}}. Anything else — including the plain-text bodies
 * Atlassian's gateway sends for unauthenticated requests — is passed through as-is.
 */
public final class AtlassianErrorMessages {

    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String ERRORS = "errors";
    private static final String TITLE = "title";
    private static final String MESSAGE_SEPARATOR = "; ";
    private static final String EMPTY_BODY = "empty response body";

    /**
     * Unstructured bodies (gateway HTML, proxy pages) are cut to this length: they reach the MCP
     * client as tool-failure text, and only their opening says anything useful.
     */
    public static final int MAX_RAW_BODY_LENGTH = 300;

    private AtlassianErrorMessages() {
    }

    public static Optional<String> extract(JsonNode root) {
        List<String> messages = new ArrayList<>();

        JsonNode errorMessages = root.path(ERROR_MESSAGES);

        if (errorMessages.isArray()) {
            for (JsonNode errorMessage : errorMessages) {
                messages.add(errorMessage.asString());
            }
        }

        JsonNode errors = root.path(ERRORS);

        if (errors.isObject()) {
            errors.properties().forEach(entry -> messages.add(entry.getKey() + ": " + entry.getValue().asString()));
        }

        if (errors.isArray()) {
            for (JsonNode error : errors) {
                JsonNode title = error.path(TITLE);
                messages.add(title.isMissingNode() ? error.toString() : title.asString());
            }
        }

        if (messages.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(String.join(MESSAGE_SEPARATOR, messages));
    }

    public static String summarize(JsonMapper jsonMapper, String body) {
        if (StringUtils.isBlank(body)) {
            return EMPTY_BODY;
        }

        try {
            return extract(jsonMapper.readTree(body)).orElseGet(() -> StringUtils.abbreviate(body, MAX_RAW_BODY_LENGTH));
        } catch (JacksonException notJson) {
            return StringUtils.abbreviate(body, MAX_RAW_BODY_LENGTH);
        }
    }
}
