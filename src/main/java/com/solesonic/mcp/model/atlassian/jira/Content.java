package com.solesonic.mcp.model.atlassian.jira;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Content(
        String type,
        List<TextContent> content
) {

    public static Builder content(List<TextContent> content) {
        return new Builder().content(content);
    }

    public static class Builder {
        private String type;
        private List<TextContent> content;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder content(List<TextContent> content) {
            this.content = content;
            return this;
        }

        public Content build() {
            return new Content(type, content);
        }
    }

}

