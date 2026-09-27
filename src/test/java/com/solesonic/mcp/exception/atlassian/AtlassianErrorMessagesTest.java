package com.solesonic.mcp.exception.atlassian;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtlassianErrorMessagesTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void jiraErrorMessagesAndFieldErrors_areJoined() {
        String body = "{\"errorMessages\":[\"First\",\"Second\"],\"errors\":{\"summary\":\"Required\"}}";

        assertEquals(Optional.of("First; Second; summary: Required"),
                AtlassianErrorMessages.extract(jsonMapper.readTree(body)));
    }

    @Test
    void confluenceErrorArray_usesTitles() {
        String body = "{\"errors\":[{\"status\":404,\"title\":\"Page not found\"}]}";

        assertEquals("Page not found", AtlassianErrorMessages.summarize(jsonMapper, body));
    }

    @Test
    void emptyErrorStructures_extractNothing() {
        String body = "{\"errorMessages\":[],\"errors\":{}}";

        assertEquals(Optional.empty(), AtlassianErrorMessages.extract(jsonMapper.readTree(body)));
    }

    @Test
    void nonJsonBody_isReturnedAsIs() {
        assertEquals("Client must be authenticated", AtlassianErrorMessages.summarize(jsonMapper, "Client must be authenticated"));
    }

    @Test
    void longNonJsonBody_isTruncatedBeforeReachingTheCaller() {
        String gatewayPage = "<html>" + "x".repeat(5_000) + "</html>";

        String summary = AtlassianErrorMessages.summarize(jsonMapper, gatewayPage);

        assertEquals(AtlassianErrorMessages.MAX_RAW_BODY_LENGTH, summary.length());
    }

    @Test
    void blankBody_isDescribed() {
        assertEquals("empty response body", AtlassianErrorMessages.summarize(jsonMapper, ""));
    }
}
