package com.solesonic.mcp.tool.atlassian;

import org.springframework.stereotype.Service;

@Service
public class JiraAgileTools {
    public JiraAgileTools() {
    }

    public record ListBoardsRequest(Integer startAt,
                                    Integer maxResults,
                                    String type,
                                    String name,
                                    String projectKeyOrId){}

    public record BoardIssuesRequest(String boardId,
                                     String jql,
                                     Integer startAt,
                                     Integer maxResults,
                                     boolean validateQuery) {}
}
