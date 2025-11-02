package com.solesonic.mcp.model.atlassian.jira;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TextContent(
        String type,
        String text,
        List<TextContent> content
) {

    public static Builder text(String text) {
        return new Builder().text(text);
    }

    public static Builder type(String type) {
        return new Builder().type(type);
    }

    public static class Builder {
        private String type;
        private String text;
        private List<TextContent> content;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder content(List<TextContent> content) {
            this.content = content;
            return this;
        }

        public TextContent build() {
            return new TextContent(type, text, content);
        }
    }
}
