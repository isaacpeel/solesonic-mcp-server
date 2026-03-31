package com.solesonic.mcp.workflow.chain;

import java.util.List;

public class UserStoryChainContext {
    private final String rawRequest;
    private String detailedStory;
    private String summary;
    private List<String> acceptanceCriteria;

    public UserStoryChainContext(String rawRequest) {
        this.rawRequest = rawRequest;
    }

    public String getRawRequest() {
        return rawRequest;
    }

    public String getDetailedStory() {
        return detailedStory;
    }

    public void setDetailedStory(String detailedStory) {
        this.detailedStory = detailedStory;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(List<String> acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }
}
