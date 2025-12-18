package com.solesonic.mcp.model.tavily;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TavilySearchRequest(
    String query,
    @JsonProperty("search_depth") String searchDepth,
    String topic,
    @JsonProperty("max_results") Integer maxResults,
    @JsonProperty("include_answer") Boolean includeAnswer,
    @JsonProperty("include_raw_content") Boolean includeRawContent,
    @JsonProperty("include_images") Boolean includeImages,
    @JsonProperty("include_domains") List<String> includeDomains,
    @JsonProperty("exclude_domains") List<String> excludeDomains,
    @JsonProperty("time_range") String timeRange
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String query;
        private String searchDepth = "basic";
        private String topic = "general";
        private Integer maxResults = 5;
        private Boolean includeAnswer = true;
        private Boolean includeRawContent = false;
        private Boolean includeImages = false;
        private List<String> includeDomains;
        private List<String> excludeDomains;
        private String timeRange;

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder searchDepth(String searchDepth) {
            this.searchDepth = searchDepth;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder includeAnswer(Boolean includeAnswer) {
            this.includeAnswer = includeAnswer;
            return this;
        }

        public Builder includeRawContent(Boolean includeRawContent) {
            this.includeRawContent = includeRawContent;
            return this;
        }

        public Builder includeImages(Boolean includeImages) {
            this.includeImages = includeImages;
            return this;
        }

        public Builder includeDomains(List<String> includeDomains) {
            this.includeDomains = includeDomains;
            return this;
        }

        public Builder excludeDomains(List<String> excludeDomains) {
            this.excludeDomains = excludeDomains;
            return this;
        }

        public Builder timeRange(String timeRange) {
            this.timeRange = timeRange;
            return this;
        }

        public TavilySearchRequest build() {
            return new TavilySearchRequest(
                query, searchDepth, topic, maxResults,
                includeAnswer, includeRawContent, includeImages,
                includeDomains, excludeDomains, timeRange
            );
        }
    }
}
